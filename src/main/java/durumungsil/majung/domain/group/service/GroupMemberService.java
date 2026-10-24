package durumungsil.majung.domain.group.service;

import durumungsil.majung.domain.group.entity.Group;
import durumungsil.majung.domain.group.entity.GroupMember;
import durumungsil.majung.domain.trip.entity.Trip;
import durumungsil.majung.domain.group.dto.response.GroupMembersResponse;
import durumungsil.majung.global.exception.BusinessException;
import durumungsil.majung.global.exception.ErrorCode;
import durumungsil.majung.domain.group.repository.GroupMemberRepository;
import durumungsil.majung.domain.preference.repository.PreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupMemberService {

    private final GroupReader groupReader;
    private final GroupMemberRepository groupMemberRepository;
    private final PreferenceRepository preferenceRepository;

    /** 그룹 멤버 목록 (참여 순). preferences_completed 는 현재 여행 회차 기준 */
    public GroupMembersResponse getMembers(Long userId, Long groupId) {
        Group group = groupReader.getMemberGroup(groupId, userId);
        List<GroupMember> members = groupMemberRepository.findAllByGroupIdWithUser(groupId);
        Trip trip = groupReader.findCurrentTrip(groupId);
        Set<Long> preferenceUserIds = trip == null ? Set.of()
                : new HashSet<>(preferenceRepository.findUserIdsByTripId(trip.getId()));
        return GroupMembersResponse.of(group, members, preferenceUserIds);
    }

    /** 그룹 탈퇴. 방장은 대표자 변경 후에만 탈퇴할 수 있다. 입력했던 취향은 남겨 두고 집계에서만 제외 */
    @Transactional
    public void leaveGroup(Long userId, Long groupId) {
        Group group = groupReader.getGroupForUpdate(groupId);
        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
        if (group.isOwner(userId)) {
            throw new BusinessException(ErrorCode.OWNER_CANNOT_LEAVE);
        }
        groupMemberRepository.delete(member);
        group.decreaseMemberCount();
    }
}
