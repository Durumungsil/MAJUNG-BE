package durumungsil.majung;

import durumungsil.majung.domain.adventure.entity.Mission;
import durumungsil.majung.domain.adventure.entity.MissionStatus;
import durumungsil.majung.domain.story.entity.Itinerary;
import durumungsil.majung.domain.preference.entity.FoodPreference;
import durumungsil.majung.domain.preference.entity.Preference;
import durumungsil.majung.domain.preference.entity.TransportType;
import durumungsil.majung.domain.store.entity.OpeningHour;
import durumungsil.majung.domain.store.entity.Store;
import durumungsil.majung.domain.trip.entity.Trip;
import durumungsil.majung.global.util.GeoUtils;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DomainRulesTests {

    @Test
    void tripRejectsReversedDatesBeforeBudgetCalculation() {
        LocalDate start = LocalDate.of(2026, 10, 18);
        assertThrows(IllegalArgumentException.class, () -> Trip.builder()
                .travelStartDate(start).travelEndDate(start.minusDays(1)).build());

        Trip dayTrip = Trip.builder().travelStartDate(start).travelEndDate(start).build();
        assertEquals(1, dayTrip.getTravelDays());
        assertEquals(10_000, dayTrip.mealBudgetPerPerson(30_000));
    }

    @Test
    void missionRejectsInvalidTimesAndKeepsCompletedRankStable() {
        Store store = Store.builder().latitude(35.18).longitude(129.07).build();
        Mission mission = Mission.builder().store(store).status(MissionStatus.AVAILABLE).build();
        LocalDateTime start = LocalDateTime.of(2026, 10, 18, 9, 0);

        assertThrows(IllegalArgumentException.class, () -> mission.start(null, 35.18, 129.07));
        assertNull(mission.getStartedAt());
        assertNull(mission.getStartLatitude());

        mission.start(start, 35.18, 129.07);
        assertThrows(IllegalArgumentException.class, () -> mission.complete(start.minusMinutes(1)));
        assertEquals(MissionStatus.AVAILABLE, mission.getStatus());
        assertNull(mission.getCompletedAt());

        mission.useHint();
        assertNotNull(mission.complete(start.plusMinutes(2)));
        assertEquals(MissionStatus.COMPLETED, mission.getStatus());
        assertThrows(IllegalStateException.class, mission::useHint);
        assertEquals(1, mission.getHintCount());
    }

    @Test
    void replacingWithExistingCollectionsPreservesValuesAndRejectsDuplicateFoods() {
        FoodPreference noodles = new FoodPreference("국수", 5);
        Preference preference = Preference.builder()
                .transportTypes(Set.of(TransportType.BUS))
                .foodPreferences(List.of(noodles))
                .build();

        preference.changeTransportTypes(preference.getTransportTypes());
        preference.changeFoodPreferences(preference.getFoodPreferences());
        assertEquals(Set.of(TransportType.BUS), preference.getTransportTypes());
        assertEquals(List.of(noodles), preference.getFoodPreferences());

        assertThrows(IllegalArgumentException.class, () -> preference.changeFoodPreferences(
                List.of(noodles, new FoodPreference("국수", 1))));
        assertEquals(List.of(noodles), preference.getFoodPreferences());
        assertThrows(IllegalArgumentException.class, () -> Preference.builder()
                .foodPreferences(List.of(noodles, noodles)).build());

        OpeningHour hours = new OpeningHour(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0));
        Store store = Store.builder().latitude(35.18).longitude(129.07)
                .openingHours(List.of(hours)).amenities(Set.of("PARKING")).build();
        store.syncDetails(null, null, null, store.getOpeningHours(), store.getAmenities(), LocalDateTime.now());
        assertEquals(List.of(hours), store.getOpeningHours());
        assertEquals(Set.of("PARKING"), store.getAmenities());
    }

    @Test
    void rejectsInvalidCoordinatesAndScheduleTimes() {
        assertThrows(IllegalArgumentException.class, () -> Store.builder().latitude(null).longitude(129.07).build());
        assertThrows(IllegalArgumentException.class, () -> GeoUtils.validate(Double.NaN, 129.07));
        assertThrows(IllegalArgumentException.class, () -> GeoUtils.validate(35.18, Double.POSITIVE_INFINITY));

        Itinerary itinerary = Itinerary.builder()
                .dayStartTime(LocalTime.of(9, 0)).dayEndTime(LocalTime.of(18, 0)).build();
        assertThrows(IllegalArgumentException.class,
                () -> itinerary.isWithinDayHours(null, LocalTime.of(10, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> itinerary.isWithinDayHours(LocalTime.of(11, 0), LocalTime.of(10, 0)));
    }
}
