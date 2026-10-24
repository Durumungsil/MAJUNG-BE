package durumungsil.majung.domain.group.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * PATCH - 보낸 필드만 수정한다. (null 은 변경 안 함)
 * group_name, max_members, owner_id 는 그룹에, region 과 여행 기간은 진행 중인 여행 회차에 반영
 */
public record UpdateGroupRequest(
        @Size(min = 1, max = 30, message = MESSAGE) String groupName,
        @Size(min = 1, message = MESSAGE) String region,
        @Min(value = 2, message = MESSAGE) @Max(value = 16, message = MESSAGE) Integer maxMembers,
        LocalDate travelStartDate,
        LocalDate travelEndDate,
        Long ownerId
) {
    static final String MESSAGE = "여행 기간 또는 인원 제한이 올바르지 않습니다.";

    public boolean changesTrip() {
        return region != null || travelStartDate != null || travelEndDate != null;
    }
}
