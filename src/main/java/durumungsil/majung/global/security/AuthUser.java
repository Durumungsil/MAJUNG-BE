package durumungsil.majung.global.security;

/**
 * 인증된 사용자. access 토큰의 sub(userId) 로 만들어져 SecurityContext 의 principal 로 들어간다.
 * 컨트롤러에서 {@code @AuthenticationPrincipal AuthUser authUser} 로 받아 쓴다.
 */
public record AuthUser(Long id) {
}
