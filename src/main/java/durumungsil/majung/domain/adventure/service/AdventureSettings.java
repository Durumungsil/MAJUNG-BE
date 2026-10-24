package durumungsil.majung.domain.adventure.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

@Component
public class AdventureSettings {
    private final int hintViewSeconds;
    private final double arrivalRadiusMeters;
    private final int maxMissionCount;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public AdventureSettings(@Value("${adventure.hint-view-seconds:${HINT_VIEW_SECONDS:30}}") int hintViewSeconds,
                             @Value("${adventure.arrival-radius-meters:${MISSION_ARRIVAL_RADIUS_METERS:100}}") double arrivalRadiusMeters,
                             @Value("${adventure.max-mission-count:${MAX_MISSION_COUNT:20}}") int maxMissionCount) {
        this(hintViewSeconds, arrivalRadiusMeters, maxMissionCount, Clock.systemUTC());
    }

    AdventureSettings(int hintViewSeconds, double arrivalRadiusMeters, int maxMissionCount, Clock clock) {
        if (hintViewSeconds <= 0 || !Double.isFinite(arrivalRadiusMeters) || arrivalRadiusMeters <= 0 || maxMissionCount <= 0) {
            throw new IllegalArgumentException("모험 설정값은 양수여야 합니다.");
        }
        this.hintViewSeconds = hintViewSeconds;
        this.arrivalRadiusMeters = arrivalRadiusMeters;
        this.maxMissionCount = maxMissionCount;
        this.clock = clock;
    }

    public int hintViewSeconds() { return hintViewSeconds; }
    public double arrivalRadiusMeters() { return arrivalRadiusMeters; }
    public int maxMissionCount() { return maxMissionCount; }
    public Instant now() { return clock.instant(); }
    public LocalDate today() { return LocalDate.ofInstant(now(), ZoneId.of("Asia/Seoul")); }
    public LocalDateTime nowUtc() { return LocalDateTime.ofInstant(now(), ZoneOffset.UTC); }
}
