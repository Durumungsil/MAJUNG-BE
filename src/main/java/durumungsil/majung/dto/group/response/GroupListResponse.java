package durumungsil.majung.dto.group.response;

import durumungsil.majung.domain.group.Group;
import durumungsil.majung.domain.trip.Trip;
import durumungsil.majung.domain.trip.TripMode;

import java.time.LocalDate;
import java.util.List;

public record GroupListResponse(List<Item> items) {

    /** trip_id, region, mode, 여행 기간, my_preferences_completed 는 현재 여행 회차 기준 */
    public record Item(
            Long groupId,
            String groupName,
            String region,
            Long tripId,
            TripMode mode,
            Integer memberCount,
            Integer maxMembers,
            LocalDate travelStartDate,
            LocalDate travelEndDate,
            boolean myPreferencesCompleted
    ) {
        public static Item of(Group group, Trip trip, boolean myPreferencesCompleted) {
            return new Item(
                    group.getId(), group.getGroupName(),
                    trip == null ? null : trip.getRegion(),
                    trip == null ? null : trip.getId(),
                    trip == null ? null : trip.getMode(),
                    group.getMemberCount(), group.getMaxMembers(),
                    trip == null ? null : trip.getTravelStartDate(),
                    trip == null ? null : trip.getTravelEndDate(),
                    myPreferencesCompleted);
        }
    }
}
