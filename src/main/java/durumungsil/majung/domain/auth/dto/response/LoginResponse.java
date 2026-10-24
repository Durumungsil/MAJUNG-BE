package durumungsil.majung.domain.auth.dto.response;

/**
 * @param firstLogin 이번 로그인으로 회원가입된 경우 true (온보딩 화면 분기용)
 */
public record LoginResponse(
        Long userId,
        boolean firstLogin,
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
    public static LoginResponse of(Long userId, boolean firstLogin, TokenResponse token) {
        return new LoginResponse(userId, firstLogin,
                token.accessToken(), token.refreshToken(), token.tokenType(), token.expiresIn());
    }
}
