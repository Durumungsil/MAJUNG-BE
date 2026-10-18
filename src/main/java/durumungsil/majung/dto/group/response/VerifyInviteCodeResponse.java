package durumungsil.majung.dto.group.response;

import durumungsil.majung.domain.group.Group;
import durumungsil.majung.domain.trip.Trip;

import java.time.LocalDateTime;

/** joinable - 이미 이 그룹의 멤버면 false (정원 초과는 403 으로 응답) */
public record VerifyInviteCodeResponse(
        Long groupId,
        String groupName,
        String region,
        Integer memberCount,
        Integer maxMembers,
        boolean joinable,
        LocalDateTime expiresAt
) {
    public static VerifyInviteCodeResponse of(Group group, Trip trip, boolean joinable) {
        return new VerifyInviteCodeResponse(
                group.getId(), group.getGroupName(),
                trip == null ? null : trip.getRegion(),
                group.getMemberCount(), group.getMaxMembers(), joinable,
                group.getInviteCodeExpiresAt());
    }
}
