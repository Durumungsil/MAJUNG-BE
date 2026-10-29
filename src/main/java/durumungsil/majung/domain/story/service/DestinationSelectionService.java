package durumungsil.majung.domain.story.service;

import durumungsil.majung.domain.group.service.GroupReader;
import durumungsil.majung.domain.place.entity.GooglePlace;
import durumungsil.majung.domain.place.service.GooglePlaceService;
import durumungsil.majung.domain.preference.entity.TravelCategory;
import durumungsil.majung.domain.story.dto.request.SaveDestinationSelectionRequest;
import durumungsil.majung.domain.story.dto.response.SaveDestinationSelectionResponse;
import durumungsil.majung.domain.story.entity.DestinationSelection;
import durumungsil.majung.domain.story.repository.DestinationSelectionRepository;
import durumungsil.majung.domain.trip.entity.Trip;
import durumungsil.majung.global.exception.BusinessException;
import durumungsil.majung.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DestinationSelectionService {

    private final GroupReader groupReader;
    private final GooglePlaceService places;
    private final DestinationSelectionRepository selectionRepository;

    /** 여행지 선택 저장 - 진행 중인 스토리모드 여행 회차의 선택을 통째로 교체한다. 방문 순서는 회차 안에서 겹치면 안 되고, 방문일은 여행 기간 안이어야 한다. */
    @Transactional
    public SaveDestinationSelectionResponse save(
            Long userId, Long groupId, SaveDestinationSelectionRequest request) {
        groupReader.getMemberGroupForUpdate(groupId, userId);
        Trip trip = groupReader.getActiveStoryTrip(groupId);
        List<SaveDestinationSelectionRequest.Item> items = request.destinations();

        Set<Integer> orders = new HashSet<>();
        for (SaveDestinationSelectionRequest.Item item : items) {
            if (!orders.add(item.order()) || !trip.includes(item.visitDate())) {
                throw new BusinessException(ErrorCode.INVALID_DESTINATION_SELECTION);
            }
        }

        Map<String, GooglePlace> selectedPlaces = new java.util.LinkedHashMap<>();
        for (var item : items) {
            if (item.category() == TravelCategory.FESTIVAL
                    || selectedPlaces.containsKey(item.placeId()))
                throw new BusinessException(ErrorCode.INVALID_DESTINATION_SELECTION);
            selectedPlaces.put(item.placeId(), places.requireLocation(item.placeId()));
        }

        selectionRepository.deleteAllByTripId(trip.getId());
        List<DestinationSelection> selections =
                items.stream()
                        .sorted(Comparator.comparing(SaveDestinationSelectionRequest.Item::order))
                        .map(
                                item ->
                                        DestinationSelection.builder()
                                                .trip(trip)
                                                .googlePlace(selectedPlaces.get(item.placeId()))
                                                .category(item.category())
                                                .visitOrder(item.order())
                                                .visitDate(item.visitDate())
                                                .build())
                        .toList();
        selectionRepository.saveAll(selections);
        selectionRepository.flush();

        return new SaveDestinationSelectionResponse(
                groupId, selections.size(), LocalDateTime.now());
    }
}
