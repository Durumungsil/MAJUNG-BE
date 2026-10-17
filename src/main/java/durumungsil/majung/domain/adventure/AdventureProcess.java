package durumungsil.majung.domain.adventure;

import durumungsil.majung.domain.trip.Trip;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 자동 여행 커리큘럼 생성 / 진행도 조회.
 * 여러 날 여행이면 날짜마다 다른 모험을 하므로 커리큘럼은 (여행 회차, 날짜)당 하나다.
 * 커리큘럼은 사용자에게 보이지 않으므로 다시 만들기는 없다(이미 있으면 409).
 */
@Entity
@Table(
        name = "adventure_processes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"trip_id", "travel_date"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdventureProcess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    /** 이 커리큘럼으로 모험하는 날짜 (여행 기간 안이어야 함) */
    @Column(name = "travel_date", nullable = false)
    private LocalDate travelDate;

    @Enumerated(EnumType.STRING)
    private AdventureStatus status;

    @Column(name = "mission_count")
    private Integer missionCount;

    @Column(name = "destination_count")
    private Integer destinationCount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private AdventureProcess(Trip trip, LocalDate travelDate, AdventureStatus status,
                             Integer missionCount, Integer destinationCount) {
        if (travelDate == null || !trip.includes(travelDate)) {
            throw new IllegalArgumentException("모험 날짜는 여행 기간 안이어야 합니다.");
        }
        this.trip = trip;
        this.travelDate = travelDate;
        this.status = status != null ? status : AdventureStatus.READY;
        this.missionCount = missionCount;
        this.destinationCount = destinationCount;
    }
}
