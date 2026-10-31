package durumungsil.majung.domain.place;

import durumungsil.majung.domain.place.entity.GooglePlace;
import durumungsil.majung.domain.preference.entity.TravelCategory;
import durumungsil.majung.global.util.GeoUtils;

/** 일정 계산용 ID·좌표·카테고리. 공급자의 이름·주소·영업시간은 포함하지 않는다. */
public record PlanningPlace(String id, Double latitude, Double longitude, TravelCategory category) {
    public static PlanningPlace of(GooglePlace place, TravelCategory category) {
        return new PlanningPlace(
                place.getPlaceId(), place.getLatitude(), place.getLongitude(), category);
    }

    public double distanceMetersFrom(double latitude, double longitude) {
        return GeoUtils.distanceMeters(latitude, longitude, this.latitude, this.longitude);
    }

    public int defaultStayMinutes() {
        return switch (category == null ? TravelCategory.SIGHTSEEING : category) {
            case CULTURE, ACTIVITY, NATURE, SIGHTSEEING -> 90;
            default -> 60;
        };
    }
}
