package durumungsil.majung.domain.story.service;

import durumungsil.majung.domain.story.entity.ItineraryItemType;
import durumungsil.majung.domain.preference.entity.TransportType;
import durumungsil.majung.domain.place.PlanningPlace;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 타임라인 시각 배치 (규칙 기반).
 * AI 플래너({@link TimelinePlanner})가 정한 날짜·순서·체류 시간도 이 클래스로 시각을 계산하고 검증한다.
 * <ul>
 *   <li>장소를 받은 순서대로 하루 시작 시각부터 채운다. 장소 사이에는 MOVE 항목을 넣는다.</li>
 *   <li>체류 시간은 Stop 에 정해진 값(AI) 또는 업종별 기본값({@link PlanningPlace#defaultStayMinutes}), 이동 시간은
 *       {@link TravelTimeEstimator} 로 어림잡는다.</li>
 *   <li>영업 시간 정보가 있으면 체류 시간 내내 영업 중일 때만 배치하고, 아직 안 열었으면 열 때까지 기다린다.</li>
 *   <li>하루 종료 시각을 넘기면 다음 날로 넘긴다. 방문일이 정해진 장소는 그날에만 배치한다.</li>
 *   <li>(선택) 교통수단에 비해 먼 이동은 남은 날이 있으면 다음 날로 넘긴다.</li>
 *   <li>여행 기간 안에 배치할 수 없는 장소가 하나라도 있으면 전체를 실패로 본다.</li>
 * </ul>
 */
public final class TimelineScheduler {

    /** 배치·영업 시간 검사 간격(분) */
    static final int SLOT_MINUTES = 10;

    private TimelineScheduler() {
    }

    /**
     * 배치할 장소.
     *
     * @param fixedDate   있으면 그 날에만 배치한다
     * @param stayMinutes 체류 시간(분). 없으면 업종별 기본값
     * @param note        VISIT 항목의 메모로 저장할 한 줄 설명 (AI 가 채운다)
     */
    public record Stop(PlanningPlace place, LocalDate fixedDate, Integer stayMinutes, String note) {

        public Stop(PlanningPlace place, LocalDate fixedDate) {
            this(place, fixedDate, null, null);
        }

        public int stayOrDefault() {
            return stayMinutes != null ? stayMinutes : place.defaultStayMinutes();
        }
    }

    public record Settings(LocalDate startDate, LocalDate endDate, TransportType transportType,
                           LocalTime dayStartTime, LocalTime dayEndTime) {
    }

    /** 배치 결과 한 줄. VISIT 은 store·시각·체류 시간·메모, MOVE 는 소요 시간·거리만 채운다 */
    public record PlannedItem(int day, LocalDate date, int sequence, ItineraryItemType type, PlanningPlace place,
                              LocalTime startAt, LocalTime endAt, Integer stayMinutes,
                              Integer durationMinutes, Integer distanceMeters, String memo) {
    }

    /** @return 배치 결과. 여행 기간 안에 모두 배치할 수 없으면 empty */
    public static Optional<List<PlannedItem>> schedule(List<Stop> stops, Settings settings) {
        return schedule(stops, settings, false);
    }

    /**
     * @param splitLongMoves true 면 교통수단의 적정 거리({@link TransportRange#maxMoveMeters})보다 먼 이동은
     *                       남은 날이 있을 때 다음 날로 넘긴다 (방문일이 정해진 장소는 넘기지 않는다)
     * @return 배치 결과. 여행 기간 안에 모두 배치할 수 없으면 empty
     */
    public static Optional<List<PlannedItem>> schedule(List<Stop> stops, Settings settings, boolean splitLongMoves) {
        int dayStart = toMinutes(settings.dayStartTime());
        int dayEnd = toMinutes(settings.dayEndTime());
        List<PlannedItem> planned = new ArrayList<>();

        LocalDate date = settings.startDate();
        int cursor = dayStart;
        PlanningPlace previous = null;
        int sequence = 0;

        for (Stop stop : stops) {
            LocalDate fixedDate = stop.fixedDate();
            if (fixedDate != null) {
                if (fixedDate.isBefore(date) || fixedDate.isAfter(settings.endDate())) {
                    return Optional.empty();
                }
                if (fixedDate.isAfter(date)) {
                    date = fixedDate;
                    cursor = dayStart;
                    previous = null;
                    sequence = 0;
                }
            }

            if (splitLongMoves && previous != null && fixedDate == null && date.isBefore(settings.endDate())
                    && TransportRange.isLongDistance(settings.transportType(), TravelTimeEstimator.routeMeters(
                    RouteOptimizer.meters(previous, stop.place())))) {
                // 먼 장소는 이동 대신 다음 날 첫 방문으로
                date = date.plusDays(1);
                cursor = dayStart;
                previous = null;
                sequence = 0;
            }

            while (true) {
                Placement placement = place(stop, previous, date, cursor, dayEnd, settings.transportType());
                if (placement != null) {
                    int day = (int) ChronoUnit.DAYS.between(settings.startDate(), date) + 1;
                    if (previous != null) {
                        planned.add(new PlannedItem(day, date, ++sequence, ItineraryItemType.MOVE, null,
                                null, null, null, placement.moveMinutes(), placement.moveMeters(), null));
                    }
                    planned.add(new PlannedItem(day, date, ++sequence, ItineraryItemType.VISIT, stop.place(),
                            toTime(placement.start()), toTime(placement.end()),
                            placement.end() - placement.start(), null, null, stop.note()));
                    cursor = placement.end();
                    previous = stop.place();
                    break;
                }
                // 이 날에는 배치할 수 없다 -> 방문일이 정해져 있거나 마지막 날이면 실패, 아니면 다음 날로
                if (fixedDate != null || !date.isBefore(settings.endDate())) {
                    return Optional.empty();
                }
                date = date.plusDays(1);
                cursor = dayStart;
                previous = null;
                sequence = 0;
            }
        }
        return Optional.of(planned);
    }

    /**
     * 체류 시간 내내 영업 중인지. 영업 시간 정보가 없으면 판단할 수 없으므로 true.
     * 시작·종료는 자정 기준 분(0 ~ 1440)이다.
     */
    public static boolean isOpenThroughout(PlanningPlace place, LocalDate date, int startMinutes, int endMinutes) {
        return true; // Places 영업시간은 요청·저장하지 않는다.
    }

    public static int toMinutes(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private static Placement place(Stop stop, PlanningPlace previous, LocalDate date, int cursor, int dayEnd,
                                   TransportType transportType) {
        PlanningPlace place = stop.place();
        int stay = stop.stayOrDefault();
        int moveMeters = 0;
        int moveMinutes = 0;
        if (previous != null) {
            moveMeters = TravelTimeEstimator.routeMeters(
                    place.distanceMetersFrom(previous.latitude(), previous.longitude()));
            moveMinutes = TravelTimeEstimator.minutes(transportType, moveMeters);
        }
        for (int start = cursor + moveMinutes; start + stay <= dayEnd; start += SLOT_MINUTES) {
            if (isOpenThroughout(place, date, start, start + stay)) {
                return new Placement(start, start + stay, moveMinutes, moveMeters);
            }
        }
        return null;
    }

    private static boolean isOpenAt(PlanningPlace place, LocalDate date, int minute) {
        LocalDateTime at = date.atStartOfDay().plusMinutes(minute);
        return true;
    }

    private static LocalTime toTime(int minutes) {
        return LocalTime.of(minutes / 60, minutes % 60);
    }

    private record Placement(int start, int end, int moveMinutes, int moveMeters) {
    }
}
