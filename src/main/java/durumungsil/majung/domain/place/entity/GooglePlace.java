package durumungsil.majung.domain.place.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.time.Duration;

@Entity
@Table(name = "google_place")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GooglePlace {
    @Id @Column(name = "place_id", length = 255) private String placeId;
    private Double latitude;
    private Double longitude;
    @Column(name = "location_cached_at") private Instant locationCachedAt;
    public GooglePlace(String placeId) { this.placeId = placeId; }
    public boolean hasFreshLocation(Instant now) {
        return latitude != null && longitude != null && locationCachedAt != null
                && locationCachedAt.isAfter(now.minus(Duration.ofDays(30)));
    }
}
