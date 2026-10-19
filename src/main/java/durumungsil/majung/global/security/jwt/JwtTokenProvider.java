package durumungsil.majung.global.security.jwt;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import durumungsil.majung.global.security.AuthUser;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * 우리 서비스의 access 토큰(HS256 JWT) 발급·검증 도구.
 * <ul>
 *   <li>sub = userId, iss = majung</li>
 *   <li>검증(서명·만료·iss)은 SecurityConfig 에서 이 클래스의 decoder 로 처리하고,
 *       통과하면 {@link #toAuthentication(Jwt)} 로 principal 을 {@link AuthUser} 로 바꾼다.</li>
 * </ul>
 */
@Component
public class JwtTokenProvider {

    public static final String ISSUER = "majung";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final JwtProperties properties;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        SecretKey key = new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key));

        NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        nimbusDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.<OAuth2TokenValidator<Jwt>>of(
                new JwtTimestampValidator(),
                new JwtClaimValidator<Instant>(JwtClaimNames.EXP, value -> value != null),
                new JwtClaimValidator<String>(JwtClaimNames.SUB, JwtTokenProvider::validUserId),
                new JwtClaimValidator<Object>(JwtClaimNames.ISS, iss -> iss != null && ISSUER.equals(iss.toString()))
        )));
        this.decoder = nimbusDecoder;
    }

    private static boolean validUserId(String subject) {
        try {
            return subject != null && Long.parseLong(subject) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    public String createAccessToken(Long userId) {
        Instant now = Instant.now();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenValidity()))
                .build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** access 토큰 유효 기간(초) - 응답의 expires_in */
    public long getAccessTokenValiditySeconds() {
        return properties.accessTokenValidity().toSeconds();
    }

    public JwtDecoder getDecoder() {
        return decoder;
    }

    /** 검증된 JWT → 인증 객체. principal 이 AuthUser 라서 컨트롤러에서 @AuthenticationPrincipal 로 받을 수 있다 */
    public AbstractAuthenticationToken toAuthentication(Jwt jwt) {
        AuthUser authUser = new AuthUser(Long.valueOf(jwt.getSubject()));
        return UsernamePasswordAuthenticationToken.authenticated(authUser, jwt, List.of());
    }
}
