package durumungsil.majung.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank(message = "구글 id_token 이 필요합니다.") String idToken
) {
}
