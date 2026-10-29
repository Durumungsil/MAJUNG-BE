package durumungsil.majung.domain.place;

import durumungsil.majung.domain.preference.entity.TravelCategory;
import java.util.*;

/** Places Table A의 검색 가능한 유형. 저장된 FESTIVAL 취향은 보존하고 검색에서만 제외한다. */
public final class PlaceTypeCatalog {
    private static final Map<TravelCategory, List<String>> TYPES = Map.of(
            TravelCategory.FOOD, List.of("restaurant", "korean_restaurant"),
            TravelCategory.CAFE, List.of("cafe", "coffee_shop", "bakery", "dessert_shop", "ice_cream_shop", "tea_house"),
            TravelCategory.SIGHTSEEING, List.of("tourist_attraction", "observation_deck", "historical_landmark", "scenic_spot"),
            TravelCategory.CULTURE, List.of("museum", "art_gallery", "history_museum", "performing_arts_theater", "cultural_center"),
            TravelCategory.ACTIVITY, List.of("amusement_park", "water_park", "bowling_alley", "karaoke", "video_arcade", "go_karting_venue", "ice_skating_rink", "adventure_sports_center"),
            TravelCategory.SHOPPING, List.of("shopping_mall", "market", "department_store"),
            TravelCategory.NATURE, List.of("park", "beach", "hiking_area", "botanical_garden", "national_park", "mountain_peak"),
            TravelCategory.HEALING, List.of("spa", "sauna", "wellness_center"));
    private static final Set<String> AVOID_TYPES;
    static {
        Set<String> types = new HashSet<>();
        TYPES.values().forEach(types::addAll);
        types.addAll(List.of("seafood_restaurant", "sushi_restaurant", "vegan_restaurant", "vegetarian_restaurant"));
        AVOID_TYPES = Set.copyOf(types);
    }
    private PlaceTypeCatalog() { }
    public static List<String> types(TravelCategory category) { return TYPES.getOrDefault(category, List.of()); }
    public static boolean allows(TravelCategory category, String type) { return types(category).contains(type); }
    public static boolean allowsAvoidType(String type) { return AVOID_TYPES.contains(type); }
    public static Set<TravelCategory> categories() { return Set.copyOf(TYPES.keySet()); }
    public static TravelCategory categoryOf(Collection<String> types, TravelCategory fallback) {
        if (types == null) return fallback;
        // 카페도 restaurant 유형을 포함할 수 있으므로 더 구체적인 분류부터 고른다.
        for (TravelCategory category : List.of(TravelCategory.CAFE, TravelCategory.SIGHTSEEING,
                TravelCategory.CULTURE, TravelCategory.ACTIVITY, TravelCategory.SHOPPING,
                TravelCategory.NATURE, TravelCategory.HEALING, TravelCategory.FOOD)) {
            if (types.stream().anyMatch(type -> allows(category, type))) return category;
        }
        return fallback;
    }
    public static String label(TravelCategory category) {
        return switch (category) {
            case FOOD -> "음식점"; case CAFE -> "카페 디저트"; case SIGHTSEEING -> "관광명소";
            case CULTURE -> "문화시설"; case ACTIVITY -> "즐길거리"; case SHOPPING -> "쇼핑";
            case NATURE -> "자연 공원"; case HEALING -> "휴식"; case FESTIVAL -> "축제";
        };
    }
    public static boolean strict(TravelCategory category) {
        return category == TravelCategory.CAFE || category == TravelCategory.FOOD;
    }
}
