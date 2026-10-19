package durumungsil.majung.global.security.jwt;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * refresh 토큰 도구.
 * refresh 토큰은 JWT 가 아니라 랜덤 문자열이고, DB 에는 원문 대신 SHA-256 해시만 저장한다.
 * (DB 가 유출돼도 토큰을 그대로 쓸 수 없고, 로그아웃·재발급 시 서버에서 바로 무효화할 수 있음)
 */
@Component
public class RefreshTokenProvider {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final JwtProperties properties;

    public RefreshTokenProvider(JwtProperties properties) {
        this.properties = properties;
    }

    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 사용할 수 없습니다.", e);
        }
    }

    public LocalDateTime expiresAt(LocalDateTime now) {
        return now.plus(properties.refreshTokenValidity());
    }
}
