package durumungsil.majung.global.util;

import java.util.List;
import java.util.Map;

/**
 * 여행 지역 표기(BUSAN / 부산 / 부산광역시 등)를 장소 데이터의 sido·sigungu 비교 값 목록으로 바꾼다.
 * 등록되지 않은 지역은 저장된 문자열 그대로 비교한다.
 */
public final class RegionAliases {

    private static final Map<String, List<String>> ALIASES = Map.of(
            "BUSAN", List.of("BUSAN", "부산", "부산광역시"),
            "SEOUL", List.of("SEOUL", "서울", "서울특별시"),
            "GANGNEUNG", List.of("GANGNEUNG", "강릉", "강릉시"),
            "JEJU", List.of("JEJU", "제주", "제주특별자치도"));

    private RegionAliases() {
    }

    /** 지역이 비어 있으면 빈 목록 */
    public static List<String> of(String region) {
        if (region == null || region.isBlank()) {
            return List.of();
        }
        String trimmed = region.strip();
        return ALIASES.values().stream()
                .filter(values -> values.contains(trimmed))
                .findFirst()
                .orElse(List.of(trimmed));
    }
}
