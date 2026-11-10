package durumungsil.majung.domain.group.dto.response;

import durumungsil.majung.domain.group.entity.Group;
import durumungsil.majung.domain.trip.entity.Trip;
import durumungsil.majung.domain.trip.entity.TripMode;
import durumungsil.majung.domain.trip.entity.TripStatus;

import java.time.LocalDate;

/**
 * trip_id, region, status, mode, 여행 기간, preferences_completed, my_preferences_completed 는 현재 여행 회차 기준.
 * my_preferences_completed 는 조회한 사용자가 현재 회차의 취향 설문을 제출했는지 (그룹 목록 응답과 같은 의미)
 */
public record GroupDetailResponse(
        Long groupId,
        String groupName,
        String region,
        Integer maxMembers,
        Integer memberCount,
        Long ownerId,
        Long tripId,
        TripStatus status,
        TripMode mode,
        long preferencesCompleted,
        boolean myPreferencesCompleted,
        LocalDate travelStartDate,
        LocalDate travelEndDate
) {
    public static GroupDetailResponse of(Group group, Trip trip, long preferencesCompleted,
                                         boolean myPreferencesCompleted) {
        return new GroupDetailResponse(
                group.getId(), group.getGroupName(),
                trip == null ? null : trip.getRegion(),
                group.getMaxMembers(), group.getMemberCount(), group.getOwner().getId(),
                trip == null ? null : trip.getId(),
                trip == null ? null : trip.getStatus(),
                trip == null ? null : trip.getMode(),
                preferencesCompleted,
                myPreferencesCompleted,
                trip == null ? null : trip.getTravelStartDate(),
                trip == null ? null : trip.getTravelEndDate());
    }
}
