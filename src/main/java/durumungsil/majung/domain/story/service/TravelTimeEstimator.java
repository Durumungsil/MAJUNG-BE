package durumungsil.majung.domain.story.service;

import durumungsil.majung.domain.preference.entity.TransportType;

/**
 * 직선거리로 이동 거리·시간을 어림잡는다 (임시 규칙).
 * TODO 길찾기 API 연동 후 실제 경로 거리·소요 시간으로 교체
 */
public final class TravelTimeEstimator {

    /** 직선거리를 실제 이동 거리로 보정하는 계수 */
    static final double DETOUR_FACTOR = 1.3;

    private TravelTimeEstimator() {
    }

    /** 직선거리(m)를 보정한 이동 거리(m) */
    public static int routeMeters(double straightMeters) {
        return (int) Math.round(Math.max(straightMeters, 0) * DETOUR_FACTOR);
    }

    /** 이동 거리(m)를 교통수단별 평균 속도와 대기 시간으로 나눈 소요 시간(분). 교통수단이 없으면 도보 */
    public static int minutes(TransportType transportType, int routeMeters) {
        if (routeMeters <= 0) {
            return 0;
        }
        TransportType type = transportType == null ? TransportType.WALK : transportType;
        double metersPerMinute = switch (type) {
            case WALK -> 70;
            case BICYCLE -> 250;
            case BUS -> 300;
            case TRAIN -> 500;
            case TAXI, CAR, OWN_CAR, AIRPLANE -> 450;
        };
        int waitMinutes = switch (type) {
            case BUS, TRAIN -> 5;
            case TAXI, AIRPLANE -> 3;
            case WALK, BICYCLE, CAR, OWN_CAR -> 0;
        };
        return (int) Math.ceil(routeMeters / metersPerMinute) + waitMinutes;
    }
}
