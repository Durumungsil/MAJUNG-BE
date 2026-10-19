package durumungsil.majung.global.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * jwt.* 설정
 *
 * @param secret               HS256 서명 키 (32바이트 이상)
 * @param accessTokenValidity  access 토큰 유효 기간
 * @param refreshTokenValidity refresh 토큰 유효 기간
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, Duration accessTokenValidity, Duration refreshTokenValidity) {

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("jwt.secret 은 32바이트 이상이어야 합니다. (HS256)");
        }
        if (accessTokenValidity == null || refreshTokenValidity == null
                || accessTokenValidity.toSeconds() <= 0 || refreshTokenValidity.toSeconds() <= 0) {
            throw new IllegalArgumentException("jwt.access-token-validity / jwt.refresh-token-validity 를 설정해야 합니다.");
        }
    }
}
