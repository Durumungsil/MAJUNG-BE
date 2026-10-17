package durumungsil.majung.domain.trip;

import durumungsil.majung.domain.group.Group;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 여행 회차. 같은 그룹(멤버 구성)이 여러 번 여행할 수 있으므로
 * 여행마다 달라지는 지역·모드·상태·기간은 그룹이 아니라 여기에 둔다.
 * 여행지 선택·타임라인·모험 커리큘럼·룰렛·취향·최소 조건은 모두 여행 회차에 묶인다.
 */
@Entity
@Table(name = "trips")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trip {

    /** 하루 식사 횟수 (한 끼 예산 계산용) */
    public static final int MEALS_PER_DAY = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    private String region;

    /** 모드 선택 전에는 null */
    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private TripMode mode;

    @Enumerated(EnumType.STRING)
    private TripStatus status;

    @Column(name = "travel_start_date")
    private LocalDate travelStartDate;

    @Column(name = "travel_end_date")
    private LocalDate travelEndDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    private Trip(Group group, String region, TripMode mode, TripStatus status,
                 LocalDate travelStartDate, LocalDate travelEndDate) {
        if (travelStartDate != null && travelEndDate != null
                && travelEndDate.isBefore(travelStartDate)) {
            throw new IllegalArgumentException("여행 종료일은 시작일보다 빠를 수 없습니다.");
        }
        this.group = group;
        this.region = region;
        this.mode = mode;
        this.status = status != null ? status : TripStatus.PLANNING;
        this.travelStartDate = travelStartDate;
        this.travelEndDate = travelEndDate;
    }

    /** 모드 선택 */
    public void selectMode(TripMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("모드는 필수입니다.");
        }
        this.mode = mode;
    }

    /** 여행 일수. 시작일과 종료일을 모두 포함 (당일치기 = 1일) */
    public int getTravelDays() {
        if (travelStartDate == null || travelEndDate == null) {
            throw new IllegalStateException("여행 기간이 설정되지 않았습니다.");
        }
        return (int) ChronoUnit.DAYS.between(travelStartDate, travelEndDate) + 1;
    }

    /** 주어진 날짜가 여행 기간(시작일~종료일, 양 끝 포함) 안인지 */
    public boolean includes(LocalDate date) {
        if (travelStartDate == null || travelEndDate == null || date == null) {
            return false;
        }
        return !date.isBefore(travelStartDate) && !date.isAfter(travelEndDate);
    }

    /** 1인 여행 예산을 여행 일수 × 하루 세 끼로 나눈 1인 한 끼 예산 */
    public int mealBudgetPerPerson(int totalBudgetPerPerson) {
        return totalBudgetPerPerson / (getTravelDays() * MEALS_PER_DAY);
    }
}
