package durumungsil.majung.global.util;

/**
 * 위도·경도 계산 유틸
 * (모험모드의 미션간의 거리를 바탕으로 미션 완료 랭크 기준에 사용할 기준 시간 계산용도)
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_METERS = 6_371_000;

    private GeoUtils() {
    }

    /** 두 좌표 사이 직선거리(m). 하버사인 공식 */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double dLat = radLat2 - radLat1;
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(radLat1) * Math.cos(radLat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(a));
    }

    public static void validate(Double latitude, Double longitude) {
        if (latitude == null || !Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || longitude == null || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("위도·경도 값이 올바르지 않습니다.");
        }
    }
}
