package durumungsil.majung.domain.trip;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 최소 조건 로드 - 여행 회차별 preferences 집계 결과 캐시
 */
@Entity
@Table(name = "trip_constraints")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripConstraint {

    @Id
    @Column(name = "trip_id")
    private Long tripId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    /** json array, 멤버 전체 합집합 */
    private String allergies;

    /** json array */
    @Column(name = "excluded_categories")
    private String excludedCategories;

    @Column(name = "source_member_count")
    private Integer sourceMemberCount;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    private TripConstraint(Trip trip, String allergies, String excludedCategories,
                           Integer sourceMemberCount) {
        this.trip = trip;
        this.allergies = allergies;
        this.excludedCategories = excludedCategories;
        this.sourceMemberCount = sourceMemberCount;
    }
}
