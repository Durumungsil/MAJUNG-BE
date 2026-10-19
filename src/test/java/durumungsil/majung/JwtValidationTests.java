package durumungsil.majung;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import durumungsil.majung.global.security.jwt.JwtProperties;
import durumungsil.majung.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtValidationTests {
    static final String KEY = "test-jwt-secret-key-for-majung-tests-0123456789";
    final JwtTokenProvider tokens = new JwtTokenProvider(new JwtProperties(KEY, Duration.ofHours(1), Duration.ofDays(14)));

    @Test
    void signedTokenWithoutExpirationIsRejected() throws Exception {
        assertThrows(JwtException.class, () -> tokens.getDecoder().decode(signed("1", false)));
    }

    @Test
    void signedTokenMustHavePositiveNumericUserId() throws Exception {
        for (String subject : new String[]{null, "", "abc", "0", "-1", "99999999999999999999999999"}) {
            assertThrows(JwtException.class, () -> tokens.getDecoder().decode(signed(subject, true)));
        }
        assertEquals("1", tokens.getDecoder().decode(tokens.createAccessToken(1L)).getSubject());
    }

    @Test
    void tokenLifetimesMustContainAtLeastOneSecond() {
        for (Duration invalid : new Duration[]{Duration.ZERO, Duration.ofSeconds(-1), Duration.ofMillis(500)}) {
            assertThrows(IllegalArgumentException.class, () -> new JwtProperties(KEY, invalid, Duration.ofDays(1)));
            assertThrows(IllegalArgumentException.class, () -> new JwtProperties(KEY, Duration.ofHours(1), invalid));
        }
    }

    private String signed(String subject, boolean withExpiration) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().issuer("majung").subject(subject);
        if (withExpiration) claims.expirationTime(Date.from(Instant.now().plusSeconds(600)));
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        jwt.sign(new MACSigner(KEY));
        return jwt.serialize();
    }
}
