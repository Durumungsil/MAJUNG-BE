package durumungsil.majung.service;

import durumungsil.majung.domain.group.Group;
import durumungsil.majung.domain.group.GroupMember;
import durumungsil.majung.domain.trip.Trip;
import durumungsil.majung.domain.trip.TripStatus;
import durumungsil.majung.domain.user.User;
import durumungsil.majung.dto.group.request.CreateGroupRequest;
import durumungsil.majung.dto.group.request.UpdateGroupRequest;
import durumungsil.majung.dto.group.response.CreateGroupResponse;
import durumungsil.majung.dto.group.response.GroupDetailResponse;
import durumungsil.majung.dto.group.response.GroupListResponse;
import durumungsil.majung.dto.group.response.UpdateGroupResponse;
import durumungsil.majung.global.exception.BusinessException;
import durumungsil.majung.global.exception.ErrorCode;
import durumungsil.majung.repository.GroupMemberRepository;
import durumungsil.majung.repository.GroupRepository;
import durumungsil.majung.repository.PreferenceRepository;
import durumungsil.majung.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupService {

    private final GroupReader groupReader;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final TripRepository tripRepository;
    private final PreferenceRepository preferenceRepository;

    /** 그룹 생성 - 생성자가 방장이 되고, 첫 여행 회차를 함께 만든다 */
    @Transactional
    public CreateGroupResponse createGroup(Long userId, CreateGroupRequest request) {
        User owner = groupReader.getUser(userId);
        validatePeriod(request.travelStartDate(), request.travelEndDate(), ErrorCode.INVALID_GROUP_CREATE);
        if (request.groupName().isBlank() || request.region().isBlank()
                || !Group.isValidMaxMembers(request.maxMembers())) {
            throw new BusinessException(ErrorCode.INVALID_GROUP_CREATE);
        }

        Group group = groupRepository.save(Group.builder()
                .groupName(request.groupName().strip())
                .maxMembers(request.maxMembers())
                .memberCount(1)
                .inviteCode(inviteCodeGenerator.generateUnique())
                .owner(owner)
                .build());
        groupMemberRepository.save(GroupMember.builder().group(group).user(owner).build());

        Trip trip = tripRepository.save(Trip.builder()
                .group(group)
                .region(request.region().strip())
                .status(TripStatus.PLANNING)
                .travelStartDate(request.travelStartDate())
                .travelEndDate(request.travelEndDate())
                .build());

        return CreateGroupResponse.of(group, trip);
    }

    /** 내가 속한 그룹 목록 (최신순). 회차 정보는 각 그룹의 현재 여행 회차 기준 */
    public GroupListResponse getMyGroups(Long userId) {
        List<Group> groups = groupMemberRepository.findAllByUserIdWithGroup(userId).stream()
                .map(GroupMember::getGroup)
                .toList();
        if (groups.isEmpty()) {
            return new GroupListResponse(List.of());
        }

        Map<Long, Trip> currentTrips = findCurrentTrips(groups.stream().map(Group::getId).toList());
        Set<Long> completedTripIds = currentTrips.isEmpty() ? Set.of() : new HashSet<>(
                preferenceRepository.findTripIdsByUserIdAndTripIdIn(userId,
                        currentTrips.values().stream().map(Trip::getId).toList()));

        List<GroupListResponse.Item> items = groups.stream()
                .map(group -> {
                    Trip trip = currentTrips.get(group.getId());
                    boolean completed = trip != null && completedTripIds.contains(trip.getId());
                    return GroupListResponse.Item.of(group, trip, completed);
                })
                .toList();
        return new GroupListResponse(items);
    }

    /** 그룹 상세 조회 */
    public GroupDetailResponse getGroup(Long userId, Long groupId) {
        Group group = groupReader.getMemberGroup(groupId, userId);
        Trip trip = groupReader.findCurrentTrip(groupId);
        long preferencesCompleted = trip == null ? 0 : preferenceRepository.countOfCurrentMembers(trip.getId(), groupId);
        return GroupDetailResponse.of(group, trip, preferencesCompleted);
    }

    /** 그룹 설정 수정 (방장만). 보낸 필드만 반영한다 */
    @Transactional
    public UpdateGroupResponse updateGroup(Long userId, Long groupId, UpdateGroupRequest request) {
        Group group = groupReader.getMemberGroupForUpdate(groupId, userId);
        groupReader.validateOwner(group, userId, ErrorCode.NOT_OWNER_UPDATE_SETTING);

        if (request.groupName() != null) {
            if (request.groupName().isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_GROUP_SETTING);
            }
            group.changeGroupName(request.groupName().strip());
        }
        if (request.maxMembers() != null) {
            if (!Group.isValidMaxMembers(request.maxMembers()) || request.maxMembers() < group.getMemberCount()) {
                throw new BusinessException(ErrorCode.INVALID_GROUP_SETTING);
            }
            group.changeMaxMembers(request.maxMembers());
        }
        if (request.ownerId() != null && !group.isOwner(request.ownerId())) {
            if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, request.ownerId())) {
                throw new BusinessException(ErrorCode.NEW_OWNER_NOT_MEMBER);
            }
            group.changeOwner(groupReader.getUser(request.ownerId()));
        }

        Trip trip;
        if (request.changesTrip()) {
            trip = groupReader.getActiveTrip(groupId);
            if (request.region() != null) {
                if (request.region().isBlank()) {
                    throw new BusinessException(ErrorCode.INVALID_GROUP_SETTING);
                }
                trip.changeRegion(request.region().strip());
            }
            if (request.travelStartDate() != null || request.travelEndDate() != null) {
                LocalDate start = request.travelStartDate() != null ? request.travelStartDate() : trip.getTravelStartDate();
                LocalDate end = request.travelEndDate() != null ? request.travelEndDate() : trip.getTravelEndDate();
                validatePeriod(start, end, ErrorCode.INVALID_GROUP_SETTING);
                trip.changeTravelPeriod(start, end);
            }
        } else {
            trip = groupReader.findCurrentTrip(groupId);
        }

        groupRepository.flush(); // updated_at 반영
        return UpdateGroupResponse.of(group, trip);
    }

    /** 그룹별 현재 여행 회차 - 진행 중인 회차 우선, 없으면 가장 최근 회차 */
    private Map<Long, Trip> findCurrentTrips(List<Long> groupIds) {
        Map<Long, Trip> active = new LinkedHashMap<>();
        Map<Long, Trip> latest = new LinkedHashMap<>();
        for (Trip trip : tripRepository.findAllByGroupIdInOrderByIdDesc(groupIds)) { // id 내림차순
            Long groupId = trip.getGroup().getId();
            latest.putIfAbsent(groupId, trip);
            if (trip.isActive()) {
                active.putIfAbsent(groupId, trip);
            }
        }
        latest.putAll(active);
        return latest;
    }

    static void validatePeriod(LocalDate start, LocalDate end, ErrorCode errorCode) {
        if (start == null || end == null || end.isBefore(start)) {
            throw new BusinessException(errorCode);
        }
    }
}
