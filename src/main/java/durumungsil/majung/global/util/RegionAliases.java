package durumungsil.majung.global.util;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 여행 지역 표기(BUSAN / 부산 / 부산광역시 / 부산 부산진구 / 서면 등)를 장소 데이터의 sido·sigungu 조회 범위로 바꾼다.
 */
public final class RegionAliases {

    /** 시·도 별칭. 값 목록은 stores.sido 와 비교한다. */
    private static final Map<String, List<String>> ALIASES = Map.of(
            "BUSAN", List.of("BUSAN", "부산", "부산광역시"),
            "DAEJEON", List.of("DAEJEON", "대전", "대전광역시"),
            "SEOUL", List.of("SEOUL", "서울", "서울특별시"),
            "GANGNEUNG", List.of("GANGNEUNG", "강릉", "강릉시"),
            "JEJU", List.of("JEJU", "제주", "제주특별자치도"));

    /** 장소 데이터(stores.sigungu)에 저장된 구·군 이름. */
    private static final Map<String, Set<String>> DISTRICTS = Map.of(
            "BUSAN", Set.of(
                    "강서구", "금정구", "기장군", "남구", "동구", "동래구", "부산진구", "북구",
                    "사상구", "사하구", "서구", "수영구", "연제구", "영도구", "중구", "해운대구"),
            "DAEJEON", Set.of("대덕구", "동구", "서구", "유성구", "중구"));

    /** 행정구역이 아닌 생활권 이름 → 시·구. */
    private static final Map<String, Area> LANDMARKS = Map.of(
            "서면", new Area("BUSAN", "부산진구"),
            "해운대", new Area("BUSAN", "해운대구"),
            "광안리", new Area("BUSAN", "수영구"),
            "남포동", new Area("BUSAN", "중구"),
            "둔산동", new Area("DAEJEON", "서구"),
            "은행동", new Area("DAEJEON", "중구"));

    private record Area(String city, String sigungu) {
    }

    /** 장소 조회 범위. sigungu 가 null 이면 시·도 전체. */
    public record Scope(List<String> sidos, String sigungu) {
    }

    private RegionAliases() {
    }

    /** 시·도 별칭 목록. 지역이 비어 있으면 빈 목록, 등록되지 않은 지역은 입력값 그대로 */
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

    /**
     * 여행 지역을 장소 조회 범위로 바꾼다.
     * "부산", "부산 부산진구", "부산진구", "서면", "부산 서면" 형태를 받는다.
     * 여러 시에 같은 이름이 있는 구(중구·서구·동구 등)는 시를 함께 적어야 하며, 알 수 없는 지역은 빈 값.
     */
    public static Optional<Scope> resolve(String region) {
        if (region == null || region.isBlank()) {
            return Optional.empty();
        }
        String[] tokens = region.strip().split("\\s+");
        if (tokens.length == 1) {
            return single(tokens[0]);
        }
        if (tokens.length == 2) {
            String city = cityKey(tokens[0]);
            return city == null ? Optional.empty() : within(city, tokens[1]);
        }
        return Optional.empty();
    }

    private static Optional<Scope> single(String token) {
        String city = cityKey(token);
        if (city != null) {
            return Optional.of(new Scope(ALIASES.get(city), null));
        }
        Area landmark = LANDMARKS.get(token);
        if (landmark != null) {
            return Optional.of(scope(landmark.city(), landmark.sigungu()));
        }
        List<String> owners = DISTRICTS.entrySet().stream()
                .filter(entry -> entry.getValue().contains(token))
                .map(Map.Entry::getKey)
                .toList();
        return owners.size() == 1 ? Optional.of(scope(owners.getFirst(), token)) : Optional.empty();
    }

    private static Optional<Scope> within(String city, String token) {
        if (DISTRICTS.getOrDefault(city, Set.of()).contains(token)) {
            return Optional.of(scope(city, token));
        }
        Area landmark = LANDMARKS.get(token);
        if (landmark != null && landmark.city().equals(city)) {
            return Optional.of(scope(city, landmark.sigungu()));
        }
        return Optional.empty();
    }

    private static String cityKey(String token) {
        return ALIASES.entrySet().stream()
                .filter(entry -> entry.getValue().contains(token))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private static Scope scope(String city, String sigungu) {
        return new Scope(ALIASES.get(city), sigungu);
    }
}
