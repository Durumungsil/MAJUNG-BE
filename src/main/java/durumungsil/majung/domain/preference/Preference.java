package durumungsil.majung.domain.preference;

import durumungsil.majung.domain.trip.Trip;
import durumungsil.majung.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 내 취향 조회 / 그룹 의견 수집 - 여행 회차마다 멤버별로 입력한다.
 * 새 회차에서는 이전 회차에 적은 취향을 불러와(copyTo) 수정하는 방식으로 제공한다.
 * 취향 통합은 이 테이블을 여행 회차 단위로 집계해서 내려줌
 */
@Entity
@Table(
        name = "preferences",
        uniqueConstraints = @UniqueConstraint(columnNames = {"trip_id", "user_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Preference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** 선호 교통수단(복수 선택). preference_transport_types 테이블에 한 행씩 저장 */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "preference_transport_types",
            joinColumns = @JoinColumn(name = "preference_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"preference_id", "transport_type"})
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "transport_type", nullable = false)
    private Set<TransportType> transportTypes = new HashSet<>();

    /** 음식별 선호 점수(1~5). preference_foods 테이블에 음식 하나당 한 행씩 저장 */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "preference_foods",
            joinColumns = @JoinColumn(name = "preference_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"preference_id", "food"})
    )
    private List<FoodPreference> foodPreferences = new ArrayList<>();

    /** json array */
    private String allergies;

    /** json array */
    @Column(name = "travel_categories")
    private String travelCategories;

    @Column(name = "budget_max")
    private Integer budgetMax;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    private Preference(Trip trip, User user, Set<TransportType> transportTypes, List<FoodPreference> foodPreferences,
                       String allergies, String travelCategories, Integer budgetMax) {
        this.trip = trip;
        this.user = user;
        if (transportTypes != null) {
            this.transportTypes.addAll(transportTypes);
        }
        if (foodPreferences != null) {
            this.foodPreferences.addAll(foodPreferences);
        }
        this.allergies = allergies;
        this.travelCategories = travelCategories;
        this.budgetMax = budgetMax;
    }

    /** 그룹 의견 수집(PUT) 시 교통수단 목록을 통째로 교체 */
    public void changeTransportTypes(Set<TransportType> transportTypes) {
        this.transportTypes.clear();
        if (transportTypes != null) {
            this.transportTypes.addAll(transportTypes);
        }
    }

    /** 그룹 의견 수집(PUT) 시 음식 선호 목록을 통째로 교체 */
    public void changeFoodPreferences(List<FoodPreference> foodPreferences) {
        this.foodPreferences.clear();
        if (foodPreferences != null) {
            this.foodPreferences.addAll(foodPreferences);
        }
    }

    /** 이전 회차 취향을 새 여행 회차로 복사 (불러와서 수정하기용) */
    public Preference copyTo(Trip newTrip) {
        return new Preference(newTrip, user, new HashSet<>(transportTypes), new ArrayList<>(foodPreferences),
                allergies, travelCategories, budgetMax);
    }
}
