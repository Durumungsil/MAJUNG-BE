package durumungsil.majung.global.security.google;

import durumungsil.majung.global.exception.BusinessException;
import durumungsil.majung.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 앱/웹에서 구글 로그인으로 받은 idToken 을 검증한다.
 * <ol>
 *   <li>서명 - 구글 공개키(JWK)로 RS256 서명 확인. 공개키는 처음 검증할 때 받아와 캐시한다.</li>
 *   <li>iss - accounts.google.com 이 발급했는지</li>
 *   <li>aud - 우리 클라이언트 ID 로 발급된 토큰인지 (다른 앱의 토큰 재사용 방지)</li>
 *   <li>exp - 만료되지 않았는지</li>
 * </ol>
 */
@Slf4j
@Component
public class GoogleIdTokenVerifier {

    private static final String GOOGLE_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> GOOGLE_ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final JwtDecoder decoder;

    public GoogleIdTokenVerifier(GoogleProperties properties) {
        List<String> clientIds = properties.clientIds();

        NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWK_SET_URI).build();
        nimbusDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.<OAuth2TokenValidator<Jwt>>of(
                new JwtTimestampValidator(),
                new JwtClaimValidator<Object>(JwtClaimNames.ISS,
                        iss -> iss != null && GOOGLE_ISSUERS.contains(iss.toString())),
                new JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.stream().anyMatch(clientIds::contains))
        )));
        this.decoder = nimbusDecoder;
    }

    public GoogleUserInfo verify(String idToken) {
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (BadJwtException e) {
            // 형식 오류, 서명 불일치, 만료, iss/aud 불일치
            log.debug("구글 idToken 검증 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INVALID_GOOGLE_TOKEN);
        } catch (JwtException e) {
            // 구글 공개키를 못 받아온 경우 등 - 토큰 문제가 아니라 통신 문제
            log.warn("구글 idToken 검증 중 오류", e);
            throw new BusinessException(ErrorCode.GOOGLE_AUTH_UNAVAILABLE);
        }
        return new GoogleUserInfo(jwt.getSubject(), jwt.getClaimAsString("email"), jwt.getClaimAsString("name"));
    }
}
