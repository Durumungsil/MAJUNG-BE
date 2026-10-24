package durumungsil.majung.domain.group.dto.response;

import durumungsil.majung.domain.group.entity.GroupMember;

import java.time.LocalDateTime;

public record JoinGroupResponse(
        Long groupId,
        Long userId,
        Integer memberCount,
        LocalDateTime joinedAt
) {
    public static JoinGroupResponse from(GroupMember member) {
        return new JoinGroupResponse(member.getGroup().getId(), member.getUser().getId(),
                member.getGroup().getMemberCount(), member.getJoinedAt());
    }
}
