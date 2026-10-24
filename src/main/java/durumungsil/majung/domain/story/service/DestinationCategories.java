package durumungsil.majung.domain.story.service;

import durumungsil.majung.domain.preference.entity.TravelCategory;
import durumungsil.majung.domain.store.entity.Type;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 장소 업종(Store.Type, 상가 데이터 대분류)과 여행 카테고리(TravelCategory) 사이의 임시 매핑.
 * 상가 데이터에는 자연·관광지 분류가 없어서 NATURE, ACTIVITY 에 해당하는 장소는 아직 없다.
 * 숙박(LODGING)은 여행지 추천 대상이 아니다.
 * TODO 장소 데이터에 관광지 분류가 들어오면 매핑 수정
 */
public final class DestinationCategories {

    private static final Map<Type, TravelCategory> CATEGORY_BY_TYPE = new EnumMap<>(Type.class);
    private static final Map<TravelCategory, String> LABELS = new EnumMap<>(TravelCategory.class);

    static {
        CATEGORY_BY_TYPE.put(Type.FOOD, TravelCategory.FOOD);
        CATEGORY_BY_TYPE.put(Type.RETAIL, TravelCategory.SHOPPING);
        CATEGORY_BY_TYPE.put(Type.ART_SPORTS, TravelCategory.CULTURE);
        CATEGORY_BY_TYPE.put(Type.SCIENCE_TECHNOLOGY, TravelCategory.EXPERIENCE);

        LABELS.put(TravelCategory.FOOD, "음식");
        LABELS.put(TravelCategory.CULTURE, "문화");
        LABELS.put(TravelCategory.EXPERIENCE, "체험");
        LABELS.put(TravelCategory.NATURE, "자연·산책");
        LABELS.put(TravelCategory.SHOPPING, "쇼핑");
        LABELS.put(TravelCategory.ACTIVITY, "액티비티");
    }

    private DestinationCategories() {
    }

    /** 업종에 해당하는 여행 카테고리. 매핑이 없으면 null */
    public static TravelCategory of(Type type) {
        return type == null ? null : CATEGORY_BY_TYPE.get(type);
    }

    /** 여행지로 추천할 수 있는 업종 전체 */
    public static Set<Type> recommendableTypes() {
        return EnumSet.copyOf(CATEGORY_BY_TYPE.keySet());
    }

    /** 주어진 여행 카테고리에 해당하는 업종들. 해당 업종이 없으면 빈 집합 */
    public static Set<Type> typesOf(Collection<TravelCategory> categories) {
        Set<Type> types = EnumSet.noneOf(Type.class);
        CATEGORY_BY_TYPE.forEach((type, category) -> {
            if (categories.contains(category)) {
                types.add(type);
            }
        });
        return types;
    }

    public static String label(TravelCategory category) {
        return category == null ? "기타" : LABELS.getOrDefault(category, category.name());
    }

    /** 타임라인 자동 배치에 쓰는 업종별 기본 체류 시간(분) */
    public static int defaultStayMinutes(Type type) {
        if (type == null) {
            return 60;
        }
        return switch (type) {
            case ART_SPORTS, SCIENCE_TECHNOLOGY -> 90;
            case FOOD, RETAIL -> 60;
            case LODGING -> 30;
        };
    }
}
