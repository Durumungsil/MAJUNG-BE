package durumungsil.majung.domain.preference.profile;

import durumungsil.majung.domain.trip.entity.Trip;

import jakarta.persistence.*;

import lombok.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "preference_profile")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PreferenceProfile {
    @Id
    @Column(name = "trip_id")
    private Long tripId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", unique = true)
    private Trip trip;

    private long version;

    @Version
    @Column(name = "row_version")
    private Long rowVersion;

    @Enumerated(EnumType.STRING)
    private ProfileStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String profile;

    @Enumerated(EnumType.STRING)
    @Column(name = "generated_by")
    private GeneratedBy generatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "run_token", length = 36)
    private String runToken;

    public PreferenceProfile(Trip trip, Instant now) {
        this.trip = trip;
        this.tripId = trip.getId();
        invalidate(now);
    }

    public void invalidate(Instant now) {
        version++;
        status = ProfileStatus.PENDING;
        profile = null;
        generatedBy = null;
        runToken = null;
        updatedAt = now;
    }

    public void start(String token, Instant now) {
        status = ProfileStatus.RUNNING;
        runToken = token;
        updatedAt = now;
    }

    public void finish(String json, GeneratedBy by, Instant now) {
        profile = json;
        generatedBy = by;
        status = ProfileStatus.DONE;
        runToken = null;
        updatedAt = now;
    }

    public void fail(Instant now) {
        status = ProfileStatus.FAILED;
        runToken = null;
        updatedAt = now;
    }
}
