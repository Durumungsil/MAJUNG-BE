package durumungsil.majung.global.security.google;

/**
 * 검증이 끝난 구글 idToken 에서 꺼낸 사용자 정보
 *
 * @param sub   구글 계정 고유 ID (유저 식별 키)
 * @param email 이메일 (참고용)
 * @param name  이름 (profile scope 가 없으면 null)
 */
public record GoogleUserInfo(String sub, String email, String name) {
}
