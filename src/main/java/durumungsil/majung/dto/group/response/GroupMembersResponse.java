package durumungsil.majung.dto.group.response;

import durumungsil.majung.domain.group.Group;
import durumungsil.majung.domain.group.GroupMember;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record GroupMembersResponse(Long groupId, List<Member> members, int count) {

    public static GroupMembersResponse of(Group group, List<GroupMember> members, Set<Long> preferenceUserIds) {
        List<Member> items = members.stream()
                .map(member -> Member.of(group, member, preferenceUserIds.contains(member.getUser().getId())))
                .toList();
        return new GroupMembersResponse(group.getId(), items, items.size());
    }

    /** is_owner 는 멤버의 user_id 와 그룹 방장(owner_id)을 비교해서 계산 */
    public record Member(
            Long userId,
            String userName,
            boolean isOwner,
            boolean preferencesCompleted,
            LocalDateTime joinedAt
    ) {
        static Member of(Group group, GroupMember member, boolean preferencesCompleted) {
            Long userId = member.getUser().getId();
            return new Member(userId, member.getUser().getUserName(), group.isOwner(userId),
                    preferencesCompleted, member.getJoinedAt());
        }
    }
}
