package durumungsil.majung.domain.story.service;

import durumungsil.majung.domain.preference.entity.TransportType;

/**
 * 교통수단별 적정 이동 거리 (임시 규칙).
 * <ul>
 *   <li>{@link #maxMoveMeters} - 한 번 이동할 때 무리 없는 최대 이동 거리(보정한 경로 m). 타임라인에서 이보다 먼 이동은
 *       다른 날로 나누고, 같은 날에 남으면 {@code long_distance} 로 표시한다.</li>
 *   <li>{@link #areaRadiusMeters} - 여행지 리스트 추천에서 한 여행 묶음으로 보는 반경(직선 m).</li>
 *   <li>{@link #nearbyRadiusMeters} - 2차 추천의 기본 반경(직선 m). 적정 이동 거리를 직선거리로 바꾼 값(최대 5km).</li>
 * </ul>
 * 비행기는 도시 안에서는 택시로 다닌다고 보고 택시·차와 같게 둔다.
 * TODO 길찾기 API 연동 후 거리 대신 소요 시간 기준으로 교체
 */
public final class TransportRange {

    private TransportRange() {
    }

    /** 한 번 이동할 때 적정한 최대 이동 거리(경로 m) */
    public static int maxMoveMeters(TransportType transportType) {
        return switch (orWalk(transportType)) {
            case WALK -> 2_000;        // 약 30분
            case BICYCLE -> 6_000;     // 약 25분
            case BUS -> 12_000;        // 약 45분
            case TRAIN -> 30_000;      // 약 65분
            case TAXI, CAR, OWN_CAR, AIRPLANE -> 20_000; // 약 45분
        };
    }

    /** 여행지 리스트 추천 반경(직선 m) */
    public static int areaRadiusMeters(TransportType transportType) {
        return switch (orWalk(transportType)) {
            case WALK -> 3_000;
            case BICYCLE -> 8_000;
            case BUS -> 15_000;
            case TRAIN -> 30_000;
            case TAXI, CAR, OWN_CAR, AIRPLANE -> 25_000;
        };
    }

    /** 2차 추천 기본 반경(직선 m): 적정 이동 거리를 직선거리로 바꿔 100m 단위로 내린 값, 최대 maxRadius */
    public static int nearbyRadiusMeters(TransportType transportType, int maxRadius) {
        int straight = (int) (maxMoveMeters(transportType) / TravelTimeEstimator.DETOUR_FACTOR);
        return Math.min(maxRadius, straight / 100 * 100);
    }

    /** 이동 거리(경로 m)가 교통수단의 적정 거리를 넘는지 */
    public static boolean isLongDistance(TransportType transportType, Integer routeMeters) {
        return routeMeters != null && routeMeters > maxMoveMeters(transportType);
    }

    private static TransportType orWalk(TransportType transportType) {
        return transportType == null ? TransportType.WALK : transportType;
    }
}
