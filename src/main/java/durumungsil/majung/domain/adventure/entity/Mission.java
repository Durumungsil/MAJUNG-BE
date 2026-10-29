package durumungsil.majung.domain.adventure.entity;

import durumungsil.majung.domain.store.entity.Store;
import durumungsil.majung.global.util.GeoUtils;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 현재 미션 조회 / 초이스 / 미션 완료(인증)
 */
@Entity
@Table(name = "missions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Mission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_id")
    private AdventureProcess process;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    /** order 는 SQL/JPQL 예약어라 컬럼명을 따옴표로 감싸고, 필드명은 missionOrder 로 사용 */
    @Column(name = "\"order\"")
    private Integer missionOrder;

    @Enumerated(EnumType.STRING)
    private MissionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "clear_rank")
    private Rank clearRank;

    private String title;

    private String objective;

    /** 초이스로 선택한 이동 경로 */
    @Enumerated(EnumType.STRING)
    @Column(name = "route_type")
    private RouteType routeType;

    @Column(name = "hint_count")
    private Integer hintCount;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** 미션 시작 시 사용자 위치. 기준 시간 = 시작 위치~미션 장소 직선거리로 계산 */
    @Column(name = "start_latitude")
    private Double startLatitude;

    @Column(name = "start_longitude")
    private Double startLongitude;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @ElementCollection
    @CollectionTable(name = "mission_rejected_places", joinColumns = @JoinColumn(name = "mission_id"))
    @Column(name = "store_id", nullable = false)
    private java.util.Set<Long> rejectedPlaceIds = new java.util.HashSet<>();

    public void replaceDestination(Store replacement) {
        if (status != MissionStatus.AVAILABLE || hintCount < 1 || rejectedPlaceIds.size() >= 3)
            throw new IllegalStateException("힌트 확인 후 현재 미션을 최대 3회 교체할 수 있습니다.");
        rejectedPlaceIds.add(store.getDestinationId());
        store = replacement;
        hintCount = 0;
        startedAt = null;
        startLatitude = null;
        startLongitude = null;
        routeType = null;
    }

    @Builder
    private Mission(AdventureProcess process, Store store, Integer missionOrder,
                    MissionStatus status, String title, String objective) {
        this.process = process;
        this.store = store;
        this.missionOrder = missionOrder;
        this.status = status != null ? status : MissionStatus.LOCKED;
        this.title = title;
        this.objective = objective;
        this.hintCount = 0;
    }

    /** 힌트(로드뷰)를 한 번 열 때마다 호출. clear_rank 계산에 쓰인다 */
    public void useHint() {
        if (status != MissionStatus.AVAILABLE) {
            throw new IllegalStateException("진행 가능한 미션에서만 힌트를 사용할 수 있습니다.");
        }
        this.hintCount++;
    }

    /** 이전 미션이 완료되면 다음 미션을 연다 (LOCKED → AVAILABLE) */
    public void unlock() {
        if (status == MissionStatus.LOCKED) {
            this.status = MissionStatus.AVAILABLE;
        }
    }

    /** 초이스: 이동 경로 선택. 미션이 시작되면 바꿀 수 없다 */
    public void chooseRoute(RouteType routeType) {
        if (startedAt != null) {
            throw new IllegalStateException("미션이 시작되어 이동 경로를 변경할 수 없습니다.");
        }
        this.routeType = routeType;
    }

    /**
     * 미션 시작. 앱이 보낸 현재 위치를 출발점으로 기록한다.
     * 이미 시작했으면 처음 시작 시각·위치를 유지한다(다시 호출해도 기록이 바뀌지 않음).
     */
    public void start(LocalDateTime now, Double latitude, Double longitude) {
        if (status != MissionStatus.AVAILABLE) {
            throw new IllegalStateException("아직 진행할 수 없는 미션입니다.");
        }
        if (startedAt != null) {
            return;
        }
        if (now == null) {
            throw new IllegalArgumentException("시작 시각은 필수입니다.");
        }
        GeoUtils.validate(latitude, longitude);
        // DB의 timestamp(6) 정밀도에 맞춰 최초 응답과 재조회 응답을 동일하게 유지한다.
        this.startedAt = now.truncatedTo(ChronoUnit.MICROS);
        this.startLatitude = latitude;
        this.startLongitude = longitude;
    }

    /** 미션 완료(위치 인증 통과 후 호출). 힌트 횟수와 걸린 시간으로 랭크를 매긴다 */
    public Rank complete(LocalDateTime now) {
        if (startedAt == null) {
            throw new IllegalStateException("시작하지 않은 미션입니다.");
        }
        if (completedAt != null) {
            throw new IllegalStateException("이미 완료된 미션입니다.");
        }
        if (now == null || now.isBefore(startedAt)) {
            throw new IllegalArgumentException("완료 시각은 시작 시각보다 빠를 수 없습니다.");
        }
        int expectedMinutes = Rank.expectedWalkingMinutes(
                store.distanceMetersFrom(startLatitude, startLongitude));
        Rank rank = Rank.of(hintCount, Duration.between(startedAt, now), expectedMinutes);
        this.completedAt = now.truncatedTo(ChronoUnit.MICROS);
        this.status = MissionStatus.COMPLETED;
        this.clearRank = rank;
        return clearRank;
    }
}
