package durumungsil.majung.domain.group.service;

import durumungsil.majung.domain.group.entity.Group;
import durumungsil.majung.domain.group.entity.GroupMember;
import durumungsil.majung.domain.user.entity.User;
import durumungsil.majung.domain.group.dto.response.InviteCodeResponse;
import durumungsil.majung.domain.group.dto.response.JoinGroupResponse;
import durumungsil.majung.domain.group.dto.response.VerifyInviteCodeResponse;
import durumungsil.majung.global.exception.BusinessException;
import durumungsil.majung.global.exception.ErrorCode;
import durumungsil.majung.domain.group.repository.GroupMemberRepository;
import durumungsil.majung.domain.group.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupInvitationService {

    private final GroupReader groupReader;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;

    /** 초대 코드 조회 (방장만). 만료됐으면 새 코드로 재발급해서 내려준다 */
    @Transactional
    public InviteCodeResponse getInviteCode(Long userId, Long groupId) {
        Group group = groupReader.getMemberGroupForUpdate(groupId, userId);
        groupReader.validateOwner(group, userId, ErrorCode.NOT_OWNER_VIEW_INVITE_CODE);
        group.reissueInviteCodeIfExpired(inviteCodeGenerator::generateUnique, LocalDateTime.now());
        return InviteCodeResponse.from(group);
    }

    /** 초대 코드 검증 - 참여 전 그룹 정보 미리보기 */
    public VerifyInviteCodeResponse verifyInviteCode(Long userId, String inviteCode) {
        validateFormat(inviteCode);
        Group group = groupRepository.findByInviteCode(inviteCode)
                .filter(found -> !found.isInviteCodeExpired(LocalDateTime.now()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_CODE_NOT_FOUND));

        boolean alreadyMember = groupMemberRepository.existsByGroupIdAndUserId(group.getId(), userId);
        if (!alreadyMember && group.isFull()) {
            throw new BusinessException(ErrorCode.GROUP_FULL);
        }
        return VerifyInviteCodeResponse.of(group, groupReader.findCurrentTrip(group.getId()), !alreadyMember);
    }

    /** 그룹 참여 - 그룹 행을 잠근 뒤 정원·중복을 확인하고 멤버로 추가 */
    @Transactional
    public JoinGroupResponse joinGroup(Long userId, Long groupId, String inviteCode) {
        validateFormat(inviteCode);
        User user = groupReader.getUser(userId);
        Group group = groupReader.getGroupForUpdate(groupId);

        if (!group.isValidInviteCode(inviteCode, LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVITE_CODE_NOT_FOUND);
        }
        if (group.isFull() || groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new BusinessException(ErrorCode.ALREADY_JOINED_OR_FULL);
        }

        group.increaseMemberCount();
        GroupMember member = groupMemberRepository.save(GroupMember.builder().group(group).user(user).build());
        return JoinGroupResponse.from(member);
    }

    private void validateFormat(String inviteCode) {
        if (!InviteCodeGenerator.isValidFormat(inviteCode)) {
            throw new BusinessException(ErrorCode.INVALID_INVITE_CODE_FORMAT);
        }
    }
}
