package durumungsil.majung.service;

import durumungsil.majung.domain.group.Group;
import durumungsil.majung.domain.trip.Trip;
import durumungsil.majung.domain.trip.TripStatus;
import durumungsil.majung.domain.user.User;
import durumungsil.majung.global.exception.BusinessException;
import durumungsil.majung.global.exception.ErrorCode;
import durumungsil.majung.repository.GroupMemberRepository;
import durumungsil.majung.repository.GroupRepository;
import durumungsil.majung.repository.TripRepository;
import durumungsil.majung.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * 그룹 관련 서비스들이 공통으로 쓰는 조회·검증 로직.
 * "인증 토큰과 요청한 리소스의 그룹 소속을 검증한다" 정책을 여기서 처리한다.
 */
@Component
@RequiredArgsConstructor
public class GroupReader {

    public static final Set<TripStatus> ACTIVE_TRIP_STATUSES = EnumSet.of(TripStatus.PLANNING, TripStatus.IN_PROGRESS);

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final TripRepository tripRepository;

    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /** 그룹이 없거나 내가 멤버가 아니면 404 (다른 그룹의 존재 여부를 노출하지 않음) */
    public Group getMemberGroup(Long groupId, Long userId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
        validateMember(groupId, userId);
        return group;
    }

    /** 멤버 수가 바뀌는 작업용 - 그룹 행을 잠그고 가져온다 */
    public Group getGroupForUpdate(Long groupId) {
        return groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }

    public void validateMember(Long groupId, Long userId) {
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new BusinessException(ErrorCode.GROUP_NOT_FOUND);
        }
    }

    public void validateOwner(Group group, Long userId, ErrorCode errorCode) {
        if (!group.isOwner(userId)) {
            throw new BusinessException(errorCode);
        }
    }

    /** 진행 중인 여행 회차 (PLANNING / IN_PROGRESS) */
    public Optional<Trip> findActiveTrip(Long groupId) {
        return tripRepository.findFirstByGroupIdAndStatusInOrderByIdDesc(groupId, ACTIVE_TRIP_STATUSES);
    }

    /** 수정 작업용 - 진행 중인 여행 회차가 없으면 409 */
    public Trip getActiveTrip(Long groupId) {
        return findActiveTrip(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_ACTIVE_TRIP));
    }

    /**
     * 조회 작업용 현재 여행 회차 - 진행 중인 회차가 있으면 그것, 모두 끝났으면 가장 최근 회차.
     * (첫 회차는 그룹 생성 시 만들어지므로 보통 null 이 아니다)
     */
    public Trip findCurrentTrip(Long groupId) {
        return findActiveTrip(groupId)
                .or(() -> tripRepository.findAllByGroupIdOrderByIdDesc(groupId).stream().findFirst())
                .orElse(null);
    }
}
