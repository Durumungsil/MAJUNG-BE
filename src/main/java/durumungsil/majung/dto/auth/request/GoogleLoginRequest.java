package durumungsil.majung.dto.auth.request;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank(message = "구글 idToken 이 필요합니다.") String idToken
) {
}
