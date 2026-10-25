package durumungsil.majung.global.util;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 자유 입력 문자열 목록 <-> json array 문자열 변환 (예: ["땅콩","새우"]).
 * 따옴표·역슬래시가 들어 있어도 안전하도록 Jackson 으로 직렬화한다.
 */
public final class StringJsonArrays {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private StringJsonArrays() {
    }

    public static String toJson(Collection<String> values) {
        return MAPPER.writeValueAsString(values == null ? List.of() : List.copyOf(values));
    }

    /** 비어 있거나 json array 가 아니면 빈 목록. 문자열이 아닌 원소는 건너뛴다 */
    public static List<String> fromJson(String json) {
        List<String> result = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node == null || !node.isArray()) {
                return result;
            }
            for (JsonNode element : node) {
                if (element.isString()) {
                    result.add(element.asString());
                }
            }
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
        return result;
    }
}
