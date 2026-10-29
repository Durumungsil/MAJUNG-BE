package durumungsil.majung.domain.story.service;

import durumungsil.majung.global.exception.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Google의 다음 페이지 토큰과 아직 전달하지 않은 후보를 서명하여 클라이언트에 돌려준다. 서버 저장소는 없다. */
@Component
public class RecommendationCursor {
    private final ObjectMapper mapper;
    private final byte[] secret;

    public RecommendationCursor(
            ObjectMapper mapper,
            @Value("${google.places.cursor-secret:${jwt.secret}}") String secret) {
        this.mapper = mapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String encode(DestinationRecommendationService.PageState state) {
        byte[] bytes = mapper.writeValueAsBytes(state);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
                + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(bytes));
    }

    public DestinationRecommendationService.PageState decode(String cursor) {
        try {
            if (cursor.length() > 200000) throw new IllegalArgumentException();
            String[] parts = cursor.split("\\.", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            byte[] bytes = Base64.getUrlDecoder().decode(parts[0]);
            if (!MessageDigest.isEqual(sign(bytes), Base64.getUrlDecoder().decode(parts[1])))
                throw new IllegalArgumentException();
            return mapper.readValue(bytes, DestinationRecommendationService.PageState.class);
        } catch (RuntimeException error) {
            throw new BusinessException(ErrorCode.INVALID_RECOMMENDATION_QUERY);
        }
    }

    private byte[] sign(byte[] bytes) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(bytes);
        } catch (Exception error) {
            throw new IllegalStateException("페이지 서명 실패");
        }
    }
}
