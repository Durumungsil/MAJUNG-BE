package durumungsil.majung.global.places;

import java.util.List;
import java.util.Optional;

public interface GooglePlacesClient {
    enum Projection {
        RECOMMENDATION("places.id,places.displayName,places.location,places.types,nextPageToken"),
        IDS_ONLY("places.id,nextPageToken");
        private final String mask;
        Projection(String mask) { this.mask = mask; }
        public String mask() { return mask; }
    }
    record Location(double latitude, double longitude) { }
    record Bounds(double lowLatitude, double lowLongitude, double highLatitude, double highLongitude) { }
    record Search(String text, String includedType, boolean strictTypeFiltering, Bounds bounds,
                  List<String> priceLevels, String pageToken) {
        public Search { priceLevels = priceLevels == null ? List.of() : List.copyOf(priceLevels); }
    }
    /** 이름은 이 응답 객체에만 존재하며 영속 엔티티나 AI 입력으로 전달하지 않는다. */
    record Place(String id, String displayName, Location location, List<String> types) {
        public Place { types = types == null ? List.of() : List.copyOf(types); }
    }
    record Page(List<Place> places, String nextPageToken) {
        public Page { places = List.copyOf(places); }
    }
    Page searchText(Search search, Projection projection);
    Optional<Location> location(String placeId);
}
