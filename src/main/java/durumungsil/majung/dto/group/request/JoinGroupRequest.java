package durumungsil.majung.dto.group.request;

import jakarta.validation.constraints.NotBlank;

public record JoinGroupRequest(
        @NotBlank(message = "초대 코드 형식이 올바르지 않습니다.") String inviteCode
) {
}
