package durumungsil.majung.dto.group.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateGroupRequest(
        @NotBlank(message = MESSAGE) @Size(max = 30, message = MESSAGE) String groupName,
        @NotBlank(message = MESSAGE) String region,
        @NotNull(message = MESSAGE) @Min(value = 2, message = MESSAGE) @Max(value = 16, message = MESSAGE) Integer maxMembers,
        @NotNull(message = MESSAGE) LocalDate travelStartDate,
        @NotNull(message = MESSAGE) LocalDate travelEndDate
) {
    static final String MESSAGE = "그룹명, 지역, 인원 제한 또는 여행 기간이 올바르지 않습니다.";
}
