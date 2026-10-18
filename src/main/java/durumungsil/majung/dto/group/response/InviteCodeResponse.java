package durumungsil.majung.dto.group.response;

import durumungsil.majung.domain.group.Group;

import java.time.LocalDateTime;

public record InviteCodeResponse(
        Long groupId,
        String groupName,
        String inviteCode,
        LocalDateTime expiresAt
) {
    public static InviteCodeResponse from(Group group) {
        return new InviteCodeResponse(group.getId(), group.getGroupName(),
                group.getInviteCode(), group.getInviteCodeExpiresAt());
    }
}
