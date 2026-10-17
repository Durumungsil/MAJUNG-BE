package durumungsil.majung.domain.store;

import durumungsil.majung.global.util.GeoUtils;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 장소(여행지·식당 등) 테이블.
 * 장소 상세조회 / 주변 장소 탐색 / 여행지 추천 / 룰렛 / 타임라인 / 미션에서 공통으로 참조한다.
 * 기본 정보는 소상공인시장진흥공단 상가 데이터(scripts/preprocess_busan_stores.py)에서 적재하고,
 * 평점·전화번호·영업시간·편의시설은 외부 장소 데이터와 동기화해서 채운다(없으면 null/빈 값).
 */
@Entity
@Table(
        name = "stores",
        uniqueConstraints = @UniqueConstraint(columnNames = {"external_provider", "external_place_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "destination_id")
    private Long destinationId;

    @Column(name = "destination_name")
    private String destinationName;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_type")
    private Type destinationType;

    private Double latitude;

    private Double longitude;

    private String sido;

    private String sigungu;

    private String address;

    private String branch;

    /** 외부 장소 API 제공자 (예: GOOGLE, KAKAO). 아직 연결 안 됐으면 null */
    @Column(name = "external_provider")
    private String externalProvider;

    /** 외부 장소 API 에서의 장소 ID. 제공자와 묶어서 유일 */
    @Column(name = "external_place_id")
    private String externalPlaceId;

    private Double rating;

    /** 1인 평균 가격(원). 외부 데이터로 채우며, 모르면 null (null 이면 예산 필터에서 제외하지 않음) */
    @Column(name = "price_per_person")
    private Integer pricePerPerson;

    private String phone;

    /** 요일별 영업 시간. store_opening_hours 테이블에 구간 하나당 한 행씩 저장 */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "store_opening_hours",
            joinColumns = @JoinColumn(name = "destination_id")
    )
    private List<OpeningHour> openingHours = new ArrayList<>();

    /** 편의시설 (예: PARKING). store_amenities 테이블에 한 행씩 저장 */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "store_amenities",
            joinColumns = @JoinColumn(name = "destination_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"destination_id", "amenity"})
    )
    @Column(name = "amenity", nullable = false)
    private Set<String> amenities = new HashSet<>();

    /** 외부 장소 데이터와 마지막으로 동기화한 시각 */
    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Builder
    private Store(String destinationName, Type destinationType, Double latitude, Double longitude,
                  String sido, String sigungu, String address, String branch, Double rating, Integer pricePerPerson,
                  String phone, List<OpeningHour> openingHours, Set<String> amenities,
                  LocalDateTime lastSyncedAt) {
        GeoUtils.validate(latitude, longitude);
        this.destinationName = destinationName;
        this.destinationType = destinationType;
        this.latitude = latitude;
        this.longitude = longitude;
        this.sido = sido;
        this.sigungu = sigungu;
        this.address = address;
        this.branch = branch;
        this.rating = rating;
        this.pricePerPerson = pricePerPerson;
        this.phone = phone;
        if (openingHours != null) {
            this.openingHours.addAll(openingHours);
        }
        if (amenities != null) {
            this.amenities.addAll(amenities);
        }
        this.lastSyncedAt = lastSyncedAt;
    }

    /**
     * 주변 장소 탐색의 isOpen 판정용.
     * 영업 시간 정보가 없으면 판단할 수 없으므로 null 을 반환한다.
     */
    public Boolean isOpenAt(LocalDateTime dateTime) {
        if (openingHours.isEmpty()) {
            return null;
        }
        return openingHours.stream()
                .anyMatch(hour -> hour.contains(dateTime.getDayOfWeek(), dateTime.toLocalTime()));
    }

    /** 외부 데이터 동기화 시 평점·상세 정보를 통째로 교체 */
    public void syncDetails(Double rating, Integer pricePerPerson, String phone, List<OpeningHour> openingHours,
                            Set<String> amenities, LocalDateTime syncedAt) {
        List<OpeningHour> newOpeningHours = openingHours == null ? List.of() : new ArrayList<>(openingHours);
        Set<String> newAmenities = amenities == null ? Set.of() : new HashSet<>(amenities);
        this.rating = rating;
        this.pricePerPerson = pricePerPerson;
        this.phone = phone;
        this.openingHours.clear();
        this.openingHours.addAll(newOpeningHours);
        this.amenities.clear();
        this.amenities.addAll(newAmenities);
        this.lastSyncedAt = syncedAt;
    }

    /** 외부 장소 API 의 장소와 연결 (제공자를 바꾸면 다시 연결) */
    public void linkExternalPlace(String provider, String externalPlaceId) {
        if (provider == null || provider.isBlank() || externalPlaceId == null || externalPlaceId.isBlank()) {
            throw new IllegalArgumentException("외부 장소 제공자와 ID는 필수입니다.");
        }
        this.externalProvider = provider;
        this.externalPlaceId = externalPlaceId;
    }

    /** 1인 한 끼 예산 안에 드는지. 가격을 모르면 걸러내지 않도록 true */
    public boolean isWithinBudget(int mealBudgetPerPerson) {
        return pricePerPerson == null || pricePerPerson <= mealBudgetPerPerson;
    }

    /** 주어진 좌표에서 이 장소까지 직선거리(m) */
    public double distanceMetersFrom(double fromLatitude, double fromLongitude) {
        GeoUtils.validate(fromLatitude, fromLongitude);
        GeoUtils.validate(latitude, longitude);
        return GeoUtils.distanceMeters(fromLatitude, fromLongitude, latitude, longitude);
    }
}
