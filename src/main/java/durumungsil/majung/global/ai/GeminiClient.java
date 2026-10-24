package durumungsil.majung.global.ai;

import java.util.Map;
import java.util.Optional;

/** Gemini 텍스트 생성. 테스트에서는 가짜 구현으로 바꿔 끼운다 */
public interface GeminiClient {

    /** API 키가 설정되어 있는지 */
    boolean isEnabled();

    /**
     * JSON 으로만 답하도록 요청하고 응답 텍스트(JSON 문자열)를 돌려준다.
     * 호출 실패·빈 응답이면 예외를 던지지 않고 empty 를 돌려준다.
     *
     * @param responseSchema 응답 JSON Schema (Gemini 구조화 출력)
     */
    Optional<String> generateJson(String prompt, Map<String, Object> responseSchema);
}
