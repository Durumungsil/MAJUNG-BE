package durumungsil.majung.domain.adventure;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.*;

class AdventureSettingsTests {
    @Test
    void usesKoreanDateAndUtcMissionTimestampsAcrossMidnight() {
        AdventureSettings settings = new AdventureSettings(30, 100, 20,
                Clock.fixed(Instant.parse("2026-10-19T15:01:00Z"), ZoneOffset.UTC));
        assertEquals(LocalDate.of(2026, 10, 20), settings.today());
        assertEquals(LocalDateTime.of(2026, 10, 19, 15, 1), settings.nowUtc());
    }

    @Test
    void rejectsInvalidPolicyValues() {
        Clock clock = Clock.systemUTC();
        assertThrows(IllegalArgumentException.class, () -> new AdventureSettings(0, 100, 20, clock));
        assertThrows(IllegalArgumentException.class, () -> new AdventureSettings(30, Double.NaN, 20, clock));
        assertThrows(IllegalArgumentException.class, () -> new AdventureSettings(30, 100, 0, clock));
    }
}
