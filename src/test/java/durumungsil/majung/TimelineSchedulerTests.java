package durumungsil.majung;

import static org.junit.jupiter.api.Assertions.*;

import durumungsil.majung.domain.place.PlanningPlace;
import durumungsil.majung.domain.preference.entity.TransportType;
import durumungsil.majung.domain.preference.entity.TravelCategory;
import durumungsil.majung.domain.story.entity.ItineraryItemType;
import durumungsil.majung.domain.story.service.RouteOptimizer;
import durumungsil.majung.domain.story.service.TimelineScheduler;
import durumungsil.majung.domain.story.service.TransportRange;
import durumungsil.majung.domain.story.service.TravelTimeEstimator;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

class TimelineSchedulerTests {

    static final LocalDate DAY1 = LocalDate.of(2026, 10, 3);

    static PlanningPlace store(String name, double latitude, double longitude) {
        return new PlanningPlace(name, latitude, longitude, TravelCategory.CULTURE);
    }

    static TimelineScheduler.Settings settings(LocalDate end, String dayStart, String dayEnd) {
        return new TimelineScheduler.Settings(
                DAY1, end, TransportType.WALK, LocalTime.parse(dayStart), LocalTime.parse(dayEnd));
    }

    @Test
    void visitsAreChainedWithMoveItemsInBetween() {
        PlanningPlace first = store("A", 35.1000, 129.0000);
        PlanningPlace second = store("B", 35.1080, 129.0000); // 약 890m 북쪽

        List<TimelineScheduler.PlannedItem> plan =
                TimelineScheduler.schedule(
                                List.of(
                                        new TimelineScheduler.Stop(first, null),
                                        new TimelineScheduler.Stop(second, null)),
                                settings(DAY1, "09:00", "21:00"))
                        .orElseThrow();

        int meters = TravelTimeEstimator.routeMeters(second.distanceMetersFrom(35.1000, 129.0000));
        int minutes = TravelTimeEstimator.minutes(TransportType.WALK, meters);
        assertEquals(
                List.of(ItineraryItemType.VISIT, ItineraryItemType.MOVE, ItineraryItemType.VISIT),
                plan.stream().map(TimelineScheduler.PlannedItem::type).toList());
        assertEquals(
                List.of(1, 2, 3),
                plan.stream().map(TimelineScheduler.PlannedItem::sequence).toList());
        assertEquals(LocalTime.of(9, 0), plan.get(0).startAt());
        assertEquals(LocalTime.of(10, 30), plan.get(0).endAt());
        assertEquals(minutes, plan.get(1).durationMinutes());
        assertEquals(meters, plan.get(1).distanceMeters());
        assertNull(plan.get(1).place());
        assertEquals(LocalTime.of(10, 30).plusMinutes(minutes), plan.get(2).startAt());
    }

    @Test
    void overflowingVisitMovesToTheNextDayWithoutMove() {
        PlanningPlace first = store("A", 35.1, 129.0);
        PlanningPlace second = store("B", 35.1, 129.001);

        List<TimelineScheduler.PlannedItem> plan =
                TimelineScheduler.schedule(
                                List.of(
                                        new TimelineScheduler.Stop(first, null),
                                        new TimelineScheduler.Stop(second, null)),
                                settings(DAY1.plusDays(1), "09:00", "11:00"))
                        .orElseThrow();

        assertEquals(2, plan.size());
        assertEquals(2, plan.get(1).day());
        assertEquals(DAY1.plusDays(1), plan.get(1).date());
        assertEquals(1, plan.get(1).sequence());
        assertEquals(LocalTime.of(9, 0), plan.get(1).startAt());
    }

    @Test
    void missingOpeningHoursDoesNotDelayTheVisit() {
        var place = store("place-id", 35.1, 129.0);
        var plan =
                TimelineScheduler.schedule(
                                List.of(new TimelineScheduler.Stop(place, DAY1)),
                                settings(DAY1, "09:00", "21:00"))
                        .orElseThrow();
        assertEquals(LocalTime.of(9, 0), plan.getFirst().startAt());
    }

    @Test
    void dayTooShortForTheStayFails() {
        PlanningPlace place = store("A", 35.1, 129.0);

        assertTrue(
                TimelineScheduler.schedule(
                                List.of(new TimelineScheduler.Stop(place, null)),
                                settings(DAY1, "09:00", "10:00"))
                        .isEmpty());
    }

    @Test
    void longMoveIsSplitToTheNextDayOnlyWhenAsked() {
        PlanningPlace first = store("A", 35.1000, 129.0);
        PlanningPlace far = store("먼 곳", 35.1500, 129.0); // 약 5.6km → 도보 적정 거리(2km) 초과
        List<TimelineScheduler.Stop> stops =
                List.of(
                        new TimelineScheduler.Stop(first, null),
                        new TimelineScheduler.Stop(far, null));
        TimelineScheduler.Settings twoDays = settings(DAY1.plusDays(1), "09:00", "21:00");

        List<TimelineScheduler.PlannedItem> split =
                TimelineScheduler.schedule(stops, twoDays, true).orElseThrow();
        assertEquals(2, split.size());
        assertEquals(DAY1.plusDays(1), split.get(1).date());

        List<TimelineScheduler.PlannedItem> together =
                TimelineScheduler.schedule(stops, twoDays, false).orElseThrow();
        assertEquals(3, together.size());
        assertTrue(
                TransportRange.isLongDistance(
                        TransportType.WALK, together.get(1).distanceMeters()));

        // 마지막 날이면 나눌 수 없으니 같은 날에 둔다
        List<TimelineScheduler.PlannedItem> lastDay =
                TimelineScheduler.schedule(stops, settings(DAY1, "09:00", "21:00"), true)
                        .orElseThrow();
        assertEquals(3, lastDay.size());
    }

    @Test
    void routeOptimizerOrdersFreeStopsByShortestPath() {
        PlanningPlace a = store("A", 35.100, 129.0);
        PlanningPlace b = store("B", 35.120, 129.0);
        PlanningPlace c = store("C", 35.110, 129.0);
        PlanningPlace d = store("D", 35.105, 129.0);

        List<String> names =
                RouteOptimizer.optimize(
                                List.of(
                                        new TimelineScheduler.Stop(a, null),
                                                new TimelineScheduler.Stop(b, null),
                                        new TimelineScheduler.Stop(c, null),
                                                new TimelineScheduler.Stop(d, null)),
                                DAY1)
                        .stream()
                        .map(stop -> stop.place().id())
                        .toList();

        assertEquals(List.of("A", "D", "C", "B"), names);
    }

    @Test
    void routeOptimizerKeepsFixedDatesAndInsertsFreeStopNextToTheClosestDay() {
        PlanningPlace dayOneA = store("1일차 A", 35.100, 129.0);
        PlanningPlace dayOneB = store("1일차 B", 35.101, 129.0);
        PlanningPlace dayTwo = store("2일차", 35.200, 129.0);
        PlanningPlace nearDayTwo = store("2일차 근처", 35.201, 129.0);

        List<TimelineScheduler.Stop> ordered =
                RouteOptimizer.optimize(
                        List.of(
                                new TimelineScheduler.Stop(nearDayTwo, null),
                                new TimelineScheduler.Stop(dayOneA, DAY1),
                                new TimelineScheduler.Stop(dayTwo, DAY1.plusDays(1)),
                                new TimelineScheduler.Stop(dayOneB, DAY1)),
                        DAY1);

        assertEquals(
                List.of("1일차 A", "1일차 B", "2일차", "2일차 근처"),
                ordered.stream().map(stop -> stop.place().id()).toList());
        List<TimelineScheduler.PlannedItem> plan =
                TimelineScheduler.schedule(
                                ordered, settings(DAY1.plusDays(1), "09:00", "21:00"), true)
                        .orElseThrow();
        assertEquals(DAY1.plusDays(1), plan.get(plan.size() - 1).date());
    }
}
