package durumungsil.majung.global.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * enum 목록 <-> json array 문자열 변환. (예: [PEANUT, MILK] <-> ["PEANUT","MILK"])
 * enum 이름은 영문 대문자/숫자/_ 뿐이라 이스케이프가 필요 없어서 직접 변환한다.
 */
public final class EnumJsonArrays {

    private EnumJsonArrays() {
    }

    public static String toJson(Collection<? extends Enum<?>> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return values.stream()
                .map(value -> "\"" + value.name() + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

    /** 저장된 값 중 현재 enum 에 없는 값(이름이 바뀌었거나 삭제된 값)은 건너뛴다 */
    public static <E extends Enum<E>> List<E> fromJson(String json, Class<E> type) {
        List<E> result = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        String body = json.strip();
        if (body.startsWith("[")) {
            body = body.substring(1);
        }
        if (body.endsWith("]")) {
            body = body.substring(0, body.length() - 1);
        }
        for (String token : body.split(",")) {
            String name = token.strip().replace("\"", "");
            if (name.isEmpty()) {
                continue;
            }
            try {
                result.add(Enum.valueOf(type, name));
            } catch (IllegalArgumentException ignored) {
                // 알 수 없는 값은 무시
            }
        }
        return result;
    }
}
