package durumungsil.majung.domain.adventure;

import durumungsil.majung.domain.store.Store;
import durumungsil.majung.domain.trip.Trip;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class AdventureStateTests {
    @Test
    void lockedAndCompletedMissionsCannotBeStartedOrHinted() {
        Mission mission = Mission.builder().store(Store.builder().latitude(35.0).longitude(129.0).build()).build();
        LocalDateTime now = LocalDateTime.of(2026, 10, 19, 0, 0);
        assertThrows(IllegalStateException.class, mission::useHint);
        assertThrows(IllegalStateException.class, () -> mission.start(now, 35.0, 129.0));
        mission.unlock();
        mission.start(now, 35.0, 129.0);
        mission.start(now.plusMinutes(1), 36.0, 128.0);
        assertEquals(now, mission.getStartedAt());
        assertEquals(35.0, mission.getStartLatitude());
        assertEquals(Rank.SS, mission.complete(now.plusMinutes(1)));
        assertThrows(IllegalStateException.class, () -> mission.start(now, 35.0, 129.0));
        assertThrows(IllegalStateException.class, mission::useHint);
    }

    @Test
    void processFollowsMissionLifecycleAndRejectsOutsideDates() {
        LocalDate date = LocalDate.of(2026, 10, 19);
        Trip trip = Trip.builder().travelStartDate(date).travelEndDate(date).build();
        assertThrows(IllegalArgumentException.class, () -> AdventureProcess.builder()
                .trip(trip).travelDate(date.plusDays(1)).build());
        AdventureProcess process = AdventureProcess.builder().trip(trip).travelDate(date).build();
        assertEquals(AdventureStatus.READY, process.getStatus());
        process.start();
        assertEquals(AdventureStatus.IN_PROGRESS, process.getStatus());
        process.finish();
        assertEquals(AdventureStatus.COMPLETED, process.getStatus());
        assertThrows(IllegalStateException.class, process::start);
    }
}
