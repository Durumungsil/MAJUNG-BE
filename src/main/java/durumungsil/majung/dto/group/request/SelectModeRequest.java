package durumungsil.majung.dto.group.request;

import jakarta.validation.constraints.NotBlank;

/** mode 는 문자열로 받아 서비스에서 TripMode 로 변환 (지원하지 않는 값이면 400 "지원하지 않는 모드입니다.") */
public record SelectModeRequest(
        @NotBlank(message = "지원하지 않는 모드입니다.") String mode
) {
}
