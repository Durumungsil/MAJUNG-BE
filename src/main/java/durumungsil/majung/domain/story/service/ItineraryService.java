package durumungsil.majung.domain.story.service;

import durumungsil.majung.domain.group.service.GroupReader;
import durumungsil.majung.domain.story.entity.Itinerary;
import durumungsil.majung.domain.story.entity.ItineraryItem;
import durumungsil.majung.domain.story.entity.ItineraryItemType;
import durumungsil.majung.domain.preference.entity.TransportType;
import durumungsil.majung.domain.place.entity.GooglePlace;
import durumungsil.majung.domain.place.PlanningPlace;
import durumungsil.majung.domain.place.service.GooglePlaceService;
import durumungsil.majung.domain.preference.entity.TravelCategory;
import java.time.Clock;
import durumungsil.majung.domain.place.repository.GooglePlaceRepository;
import durumungsil.majung.domain.trip.entity.Trip;
import durumungsil.majung.domain.story.dto.request.CreateTimelineRequest;
import durumungsil.majung.domain.story.dto.request.UpdateTimelineItemRequest;
import durumungsil.majung.domain.story.dto.response.TimelineItemResponse;
import durumungsil.majung.domain.story.dto.response.TimelineResponse;
import durumungsil.majung.domain.story.dto.response.UpdateTimelineItemResponse;
import durumungsil.majung.global.exception.BusinessException;
import durumungsil.majung.global.exception.ErrorCode;
import durumungsil.majung.domain.story.repository.DestinationSelectionRepository;
import durumungsil.majung.domain.story.repository.ItineraryItemRepository;
import durumungsil.majung.domain.story.repository.ItineraryRepository;
import durumungsil.majung.domain.preference.repository.PreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItineraryService {

    static final LocalTime DEFAULT_DAY_START = LocalTime.of(9, 0);
    static final LocalTime DEFAULT_DAY_END = LocalTime.of(21, 0);
    static final TransportType DEFAULT_TRANSPORT = TransportType.BUS;

    private final GroupReader groupReader;
    private final GooglePlaceRepository placeRepository;
    private final GooglePlaceService places;
    private final Clock clock;
    private final PreferenceRepository preferenceRepository;
    private final DestinationSelectionRepository selectionRepository;
    private final ItineraryRepository itineraryRepository;
    private final ItineraryItemRepository itemRepository;
    private final TimelinePlanner timelinePlanner;

    /**
     * AI 타임라인 생성 - 진행 중인 스토리모드 여행 회차에 타임라인을 만든다.
     * Gemini 로 방문일·순서·체류 시간을 정하고, 키가 없거나 실패하거나 동선이 나쁘면 규칙 기반으로 만든다
     * ({@link TimelinePlanner}). 규칙 기반은 이동 거리가 짧은 순서로 다시 정렬하고 먼 장소는 다른 날로 나눈다.
     * 회차에 이미 타임라인이 있으면 지우고 새로 만든다 (회차당 타임라인 하나).
     */
    @Transactional
    public TimelineResponse createTimeline(Long userId, Long groupId, CreateTimelineRequest request) {
        // AI 호출은 오래 걸릴 수 있어서 그룹 잠금은 저장 직전에 건다
        groupReader.getMemberGroup(groupId, userId);
        Trip trip = groupReader.getActiveStoryTrip(groupId);

        LocalDate startDate = request.startDate() != null ? request.startDate() : trip.getTravelStartDate();
        LocalDate endDate = request.endDate() != null ? request.endDate() : trip.getTravelEndDate();
        LocalTime dayStart = request.dayStartTime() != null ? request.dayStartTime() : DEFAULT_DAY_START;
        LocalTime dayEnd = request.dayEndTime() != null ? request.dayEndTime() : DEFAULT_DAY_END;
        if (startDate == null || endDate == null || endDate.isBefore(startDate)
                || !trip.includes(startDate) || !trip.includes(endDate) || !dayEnd.isAfter(dayStart)) {
            throw new BusinessException(ErrorCode.INVALID_TIMELINE_REQUEST);
        }
        GroupTaste taste = GroupTaste.from(preferenceRepository.findAllOfCurrentMembers(trip.getId(), groupId));
        TransportType transport = request.transportType() != null
                ? parseTransport(request.transportType())
                : taste.primaryTransportOr(DEFAULT_TRANSPORT);

        List<TimelineScheduler.Stop> stops = loadStops(trip, request.placeIds(), startDate, endDate);
        if (stops.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_DESTINATION_FOR_TIMELINE);
        }
        TimelinePlanner.Result plan = timelinePlanner.plan(stops,
                        new TimelineScheduler.Settings(startDate, endDate, transport, dayStart, dayEnd),
                        taste.categoryVotes())
                .orElseThrow(() -> new BusinessException(ErrorCode.TIMELINE_UNSCHEDULABLE));

        groupReader.getMemberGroupForUpdate(groupId, userId);
        if (!groupReader.getActiveStoryTrip(groupId).getId().equals(trip.getId())) {
            // AI 응답을 기다리는 사이 여행 회차가 바뀐 경우
            throw new BusinessException(ErrorCode.NO_ACTIVE_TRIP);
        }
        deleteTimelines(trip.getId());
        Itinerary itinerary = itineraryRepository.save(Itinerary.builder()
                .trip(trip)
                .startDate(startDate)
                .endDate(endDate)
                .transportType(transport)
                .dayStartTime(dayStart)
                .dayEndTime(dayEnd)
                .build());
        List<ItineraryItem> items = plan.items().stream()
                .map(planned -> ItineraryItem.builder()
                        .itinerary(itinerary)
                        .day(planned.day())
                        .date(planned.date())
                        .sequence(planned.sequence())
                        .type(planned.type())
                        .googlePlace(planned.place()==null?null:placeRepository.getReferenceById(planned.place().id()))
                        .startAt(planned.startAt())
                        .endAt(planned.endAt())
                        .stayMinutes(planned.stayMinutes())
                        .durationMinutes(planned.durationMinutes())
                        .distanceMeters(planned.distanceMeters())
                        .memo(planned.memo())
                        .build())
                .toList();
        itemRepository.saveAll(items);
        itineraryRepository.flush(); // generated_at / updated_at 반영

        return toResponse(groupId, itinerary, items, plan.generatedBy());
    }

    /** 타임라인 조회 */
    public TimelineResponse getTimeline(Long userId, Long groupId, Long itineraryId) {
        groupReader.getMemberGroup(groupId, userId);
        Itinerary itinerary = getGroupItinerary(groupId, itineraryId);
        return toResponse(groupId, itinerary, itemRepository.findAllByItineraryIdWithPlace(itineraryId), null);
    }

    /**
     * 타임라인 항목 수정 - VISIT 의 시간·메모만 바꾼다.
     * 하루 일정 시간을 벗어나거나 같은 날 다른 방문과 겹치면 400.
     * 영업 시간과 맞지 않으면 저장은 하고 has_conflict=true 로 알려준다.
     */
    @Transactional
    public UpdateTimelineItemResponse updateItem(Long userId, Long groupId, Long itineraryId, Long itemId,
                                                 UpdateTimelineItemRequest request) {
        groupReader.getMemberGroupForUpdate(groupId, userId);
        Itinerary itinerary = getGroupItinerary(groupId, itineraryId);
        List<ItineraryItem> allItems = itemRepository.findAllByItineraryIdWithPlace(itineraryId);
        ItineraryItem item = allItems.stream()
                .filter(candidate -> candidate.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.ITINERARY_ITEM_NOT_FOUND));
        if (item.getType() != ItineraryItemType.VISIT) {
            throw new BusinessException(ErrorCode.MOVE_ITEM_NOT_EDITABLE);
        }

        LocalTime startAt = request.startAt() != null ? request.startAt() : item.getStartAt();
        LocalTime endAt = request.endAt() != null ? request.endAt() : item.getEndAt();
        String memo = request.memo() == null ? item.getMemo()
                : (request.memo().isBlank() ? null : request.memo().strip());
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)
                || !itinerary.isWithinDayHours(startAt, endAt)) {
            throw new BusinessException(ErrorCode.INVALID_ITINERARY_ITEM_SCHEDULE);
        }
        boolean overlaps = allItems.stream()
                .filter(other -> !other.getId().equals(item.getId()))
                .filter(other -> other.getType() == ItineraryItemType.VISIT)
                .filter(other -> other.getDay().equals(item.getDay()))
                .filter(other -> other.getStartAt() != null && other.getEndAt() != null)
                .anyMatch(other -> startAt.isBefore(other.getEndAt()) && other.getStartAt().isBefore(endAt));
        if (overlaps) {
            throw new BusinessException(ErrorCode.INVALID_ITINERARY_ITEM_SCHEDULE);
        }

        item.changeSchedule(startAt, endAt, memo);
        itemRepository.flush();
        boolean hasConflict = false; // 영업시간을 요청하지 않으므로 충돌 판정 대상이 아니다.
        return new UpdateTimelineItemResponse(itineraryId, itemId, startAt, endAt, memo, hasConflict,
                LocalDateTime.now());
    }

    // ---------------------------------------------------------------- 내부

    /** 요청한 그룹의 타임라인이 아니면 404 (다른 그룹 타임라인의 존재 여부를 노출하지 않음) */
    private Itinerary getGroupItinerary(Long groupId, Long itineraryId) {
        return itineraryRepository.findById(itineraryId)
                .filter(itinerary -> itinerary.getTrip().getGroup().getId().equals(groupId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ITINERARY_NOT_FOUND));
    }

    /**
     * 배치할 장소 목록.
     * place_ids 를 보내면 방문일 없이, 안 보내면 저장한 여행지 선택을 방문일에 맞춰 배치한다.
     * 방문 순서는 플래너가 동선에 맞춰 다시 정한다 (받은 순서는 동률일 때만 쓴다).
     */
    private List<TimelineScheduler.Stop> loadStops(Trip trip, List<String> placeIds,
                                                   LocalDate startDate, LocalDate endDate) {
        var selected = selectionRepository.findAllByTripIdWithPlace(trip.getId());
        if (placeIds != null && !placeIds.isEmpty()) {
            if (new java.util.HashSet<>(placeIds).size() != placeIds.size())
                throw new BusinessException(ErrorCode.INVALID_TIMELINE_REQUEST);
            Map<String, TravelCategory> categories = new java.util.HashMap<>();
            selected.forEach(s -> categories.put(s.getGooglePlace().getPlaceId(),s.getCategory()));
            return placeIds.stream().map(id -> new TimelineScheduler.Stop(
                    PlanningPlace.of(places.requireLocation(id),categories.getOrDefault(id,TravelCategory.SIGHTSEEING)),null)).toList();
        }
        return selected.stream().filter(selection -> !selection.getVisitDate().isBefore(startDate)
                        && !selection.getVisitDate().isAfter(endDate))
                .map(selection -> new TimelineScheduler.Stop(PlanningPlace.of(
                        places.requireLocation(selection.getGooglePlace().getPlaceId()),selection.getCategory()),selection.getVisitDate()))
                .toList();
    }

    private void deleteTimelines(Long tripId) {
        List<Long> ids = itineraryRepository.findIdsByTripId(tripId);
        if (!ids.isEmpty()) {
            itemRepository.deleteAllByItineraryIdIn(ids);
            itineraryRepository.deleteAllByIdIn(ids);
        }
    }

    private static TransportType parseTransport(String value) {
        String name = value.strip();
        return Arrays.stream(TransportType.values())
                .filter(type -> type.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TIMELINE_REQUEST));
    }

    private TimelineResponse toResponse(Long groupId, Itinerary itinerary, List<ItineraryItem> items,
                                               String generatedBy) {
        // day 별로 묶고 (입력은 day, sequence 순), MOVE 는 앞뒤 VISIT 을 출발지·도착지로 채운다
        Map<Integer, List<ItineraryItem>> byDay = new LinkedHashMap<>();
        items.stream()
                .sorted((a, b) -> a.getDay().equals(b.getDay())
                        ? a.getSequence().compareTo(b.getSequence())
                        : a.getDay().compareTo(b.getDay()))
                .forEach(item -> byDay.computeIfAbsent(item.getDay(), key -> new ArrayList<>()).add(item));

        List<TimelineResponse.Day> days = new ArrayList<>();
        byDay.forEach((day, dayItems) -> {
            List<TimelineItemResponse> responses = new ArrayList<>();
            for (int i = 0; i < dayItems.size(); i++) {
                ItineraryItem item = dayItems.get(i);
                if (item.getType() == ItineraryItemType.MOVE) {
                    GooglePlace from = i > 0 ? dayItems.get(i - 1).getGooglePlace() : null;
                    GooglePlace to = i + 1 < dayItems.size() ? dayItems.get(i + 1).getGooglePlace() : null;
                    responses.add(new TimelineItemResponse(item.getId(),item.getSequence(),item.getType(),
                            null,null,null,null,null,null,
                            from==null?null:from.getPlaceId(),latitude(from),longitude(from),
                            to==null?null:to.getPlaceId(),latitude(to),longitude(to),
                            item.getDurationMinutes(),item.getDistanceMeters(),
                            TransportRange.isLongDistance(itinerary.getTransportType(),item.getDistanceMeters()),item.getMemo()));
                } else {
                    GooglePlace place=item.getGooglePlace();
                    responses.add(new TimelineItemResponse(item.getId(),item.getSequence(),item.getType(),
                            place==null?null:place.getPlaceId(),latitude(place),longitude(place),
                            item.getStartAt(),item.getEndAt(),item.getStayMinutes(),
                            null,null,null,null,null,null,null,null,null,item.getMemo()));
                }
            }
            days.add(new TimelineResponse.Day(day, dayItems.get(0).getDate(), responses));
        });

        return new TimelineResponse(itinerary.getId(), groupId, itinerary.getStartDate(), itinerary.getEndDate(),
                itinerary.getTransportType(), TransportRange.maxMoveMeters(itinerary.getTransportType()),
                itinerary.getDayStartTime(), itinerary.getDayEndTime(), days,
                itinerary.getGeneratedAt(), itinerary.getUpdatedAt(), generatedBy);
    }
    private Double latitude(GooglePlace place) { return place!=null && place.hasFreshLocation(clock.instant())?place.getLatitude():null; }
    private Double longitude(GooglePlace place) { return place!=null && place.hasFreshLocation(clock.instant())?place.getLongitude():null; }

}
