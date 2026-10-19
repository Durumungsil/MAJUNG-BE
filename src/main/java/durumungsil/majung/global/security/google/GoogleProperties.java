package durumungsil.majung.global.security.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * google.* 설정
 *
 * @param clientIds 구글 OAuth 클라이언트 ID 목록 (웹/안드로이드/iOS 가 다르면 콤마로 여러 개).
 *                  idToken 의 aud 가 이 중 하나여야 우리 앱에서 발급받은 토큰으로 인정한다.
 */
@ConfigurationProperties(prefix = "google")
public record GoogleProperties(List<String> clientIds) {

    public GoogleProperties {
        if (clientIds == null || clientIds.isEmpty() || clientIds.stream().allMatch(String::isBlank)) {
            throw new IllegalArgumentException("google.client-ids 를 설정해야 합니다.");
        }
        clientIds = clientIds.stream().map(String::trim).filter(id -> !id.isEmpty()).toList();
    }
}
