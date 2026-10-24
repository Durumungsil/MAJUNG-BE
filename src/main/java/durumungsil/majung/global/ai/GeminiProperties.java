package durumungsil.majung.global.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * gemini.* 설정. api-key 가 비어 있으면 Gemini 를 부르지 않는다 (AI 타임라인은 규칙 기반으로 동작).
 *
 * @param apiKey  Gemini API 키 (.env 의 GEMINI_API_KEY)
 * @param model   모델 코드 (기본 gemini-3.5-flash-lite)
 * @param timeout 요청 하나의 연결·응답 대기 시간 (기본 30초)
 * @param baseUrl Gemini API 주소
 */
@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(String apiKey, String model, Duration timeout, String baseUrl) {

    public static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    public static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";

    public GeminiProperties {
        apiKey = apiKey == null ? "" : apiKey.strip();
        model = model == null || model.isBlank() ? DEFAULT_MODEL : model.strip();
        timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? DEFAULT_TIMEOUT : timeout;
        baseUrl = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.strip();
    }

    public boolean hasApiKey() {
        return !apiKey.isEmpty();
    }
}
