package durumungsil.majung.global.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gemini generateContent REST 호출.
 * 요청 본문은 Map 으로 만들어 전역 snake_case 설정과 상관없이 Gemini 필드 이름(camelCase)을 그대로 보낸다.
 */
@Slf4j
@Component
public class RestGeminiClient implements GeminiClient {

    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public RestGeminiClient(GeminiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public boolean isEnabled() {
        return properties.hasApiKey();
    }

    @Override
    public Optional<String> generateJson(String prompt, Map<String, Object> responseSchema) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseJsonSchema", responseSchema));
        try {
            String response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", properties.model())
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
            return extractText(response);
        } catch (RestClientResponseException e) {
            log.warn("Gemini 호출 실패 (model={}, status={})", properties.model(), e.getStatusCode().value());
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("Gemini 호출 실패 (model={}, type={})", properties.model(), e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** candidates[0].content.parts[].text 를 이어 붙인다 (생각 과정 part 는 뺀다) */
    private Optional<String> extractText(String response) {
        if (response == null || response.isBlank()) {
            log.warn("Gemini 응답이 비어 있습니다.");
            return Optional.empty();
        }
        JsonNode candidate = objectMapper.readTree(response).path("candidates").path(0);
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            JsonNode thought = part.path("thought");
            if (thought.isBoolean() && thought.asBoolean()) {
                continue;
            }
            JsonNode value = part.path("text");
            if (value.isString()) {
                text.append(value.asString());
            }
        }
        if (text.isEmpty()) {
            log.warn("Gemini 응답에 텍스트가 없습니다 (finishReason={}).", candidate.path("finishReason"));
            return Optional.empty();
        }
        return Optional.of(text.toString());
    }

}
