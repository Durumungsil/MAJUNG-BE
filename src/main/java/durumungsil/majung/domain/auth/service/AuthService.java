package durumungsil.majung.domain.auth.service;

import durumungsil.majung.domain.auth.entity.RefreshToken;
import durumungsil.majung.domain.user.entity.User;
import durumungsil.majung.domain.auth.dto.response.LoginResponse;
import durumungsil.majung.domain.auth.dto.response.TokenResponse;
import durumungsil.majung.global.exception.BusinessException;
import durumungsil.majung.global.exception.ErrorCode;
import durumungsil.majung.global.security.google.GoogleIdTokenVerifier;
import durumungsil.majung.global.security.google.GoogleUserInfo;
import durumungsil.majung.global.security.jwt.JwtTokenProvider;
import durumungsil.majung.global.security.jwt.RefreshTokenProvider;
import durumungsil.majung.domain.auth.repository.RefreshTokenRepository;
import durumungsil.majung.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenProvider refreshTokenProvider;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    /** 구글 로그인 - idToken 검증 → (없으면 가입) → 토큰 발급 */
    public LoginResponse loginWithGoogle(String idToken) {
        GoogleUserInfo google = googleIdTokenVerifier.verify(idToken);

        Optional<User> existing = userRepository.findByGoogleSub(google.sub());
        existing.ifPresent(found -> found.updateProfile(google.email(), google.name()));
        User user = existing.orElseGet(() -> userRepository.save(User.builder()
                .googleSub(google.sub())
                .email(google.email())
                .userName(google.name())
                .build()));

        refreshTokenRepository.deleteExpired(user.getId(), LocalDateTime.now());
        return LoginResponse.of(user.getId(), existing.isEmpty(), issueTokens(user));
    }

    /** 토큰 재발급 - 쓰인 refresh 토큰은 폐기하고 새 토큰 쌍을 준다(토큰 회전) */
    public TokenResponse refresh(String rawRefreshToken) {
        RefreshToken saved = refreshTokenRepository.findByTokenHash(refreshTokenProvider.hash(rawRefreshToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        if (saved.isExpired(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (refreshTokenRepository.revoke(saved.getId()) == 0) {
            // 동시에 들어온 다른 요청이 먼저 사용함
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        return issueTokens(saved.getUser());
    }

    /** 로그아웃 - 이 기기의 refresh 토큰만 폐기 (access 토큰은 만료까지 유효하므로 클라이언트에서도 삭제) */
    public void logout(Long userId, String rawRefreshToken) {
        refreshTokenRepository.revokeByTokenHash(refreshTokenProvider.hash(rawRefreshToken), userId);
    }

    private TokenResponse issueTokens(User user) {
        String refreshToken = refreshTokenProvider.generate();
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(refreshTokenProvider.hash(refreshToken))
                .expiresAt(refreshTokenProvider.expiresAt(LocalDateTime.now()))
                .build());

        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        return TokenResponse.of(accessToken, refreshToken, jwtTokenProvider.getAccessTokenValiditySeconds());
    }
}
