package durumungsil.majung.service;

import durumungsil.majung.domain.group.Group;
import durumungsil.majung.domain.trip.Trip;
import durumungsil.majung.domain.trip.TripMode;
import durumungsil.majung.domain.trip.TripStatus;
import durumungsil.majung.dto.group.response.SelectModeResponse;
import durumungsil.majung.dto.trip.request.CreateTripRequest;
import durumungsil.majung.dto.trip.response.TripListResponse;
import durumungsil.majung.dto.trip.response.TripResponse;
import durumungsil.majung.global.exception.BusinessException;
import durumungsil.majung.global.exception.ErrorCode;
import durumungsil.majung.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripService {

    private final GroupReader groupReader;
    private final TripRepository tripRepository;

    /** 모드 선택 (방장만) - 진행 중인 여행 회차에 저장 */
    @Transactional
    public SelectModeResponse selectMode(Long userId, Long groupId, String modeValue) {
        Group group = groupReader.getMemberGroupForUpdate(groupId, userId);
        groupReader.validateOwner(group, userId, ErrorCode.NOT_OWNER_SELECT_MODE);
        TripMode mode = parseMode(modeValue);

        Trip trip = groupReader.getActiveTrip(groupId);
        trip.selectMode(mode);
        tripRepository.flush(); // updated_at 반영
        return SelectModeResponse.of(groupId, trip);
    }

    /** 여행 회차 생성 (방장만). 그룹당 진행 중인 여행은 하나만 */
    @Transactional
    public TripResponse createTrip(Long userId, Long groupId, CreateTripRequest request) {
        // 동시에 두 회차가 만들어지지 않도록 그룹 행을 잠근다
        Group group = groupReader.getGroupForUpdate(groupId);
        groupReader.validateMember(groupId, userId);
        groupReader.validateOwner(group, userId, ErrorCode.NOT_OWNER_CREATE_TRIP);
        if (request.region().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TRIP);
        }
        GroupService.validatePeriod(request.travelStartDate(), request.travelEndDate(), ErrorCode.INVALID_TRIP);
        if (tripRepository.existsByGroupIdAndStatusIn(groupId, GroupReader.ACTIVE_TRIP_STATUSES)) {
            throw new BusinessException(ErrorCode.ACTIVE_TRIP_EXISTS);
        }

        Trip trip = tripRepository.save(Trip.builder()
                .group(group)
                .region(request.region().strip())
                .status(TripStatus.PLANNING)
                .travelStartDate(request.travelStartDate())
                .travelEndDate(request.travelEndDate())
                .build());
        return TripResponse.from(trip);
    }

    /** 여행 회차 목록 (최신순) */
    public TripListResponse getTrips(Long userId, Long groupId) {
        groupReader.getMemberGroup(groupId, userId);
        return TripListResponse.of(groupId, tripRepository.findAllByGroupIdOrderByIdDesc(groupId));
    }

    private TripMode parseMode(String value) {
        return Arrays.stream(TripMode.values())
                .filter(mode -> mode.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_MODE));
    }
}
