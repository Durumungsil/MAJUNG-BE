package durumungsil.majung.dto.group.response;

import durumungsil.majung.domain.group.Group;
import durumungsil.majung.domain.trip.Trip;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CreateGroupResponse(
        Long groupId,
        String groupName,
        String region,
        Integer maxMembers,
        Integer memberCount,
        Long ownerId,
        String inviteCode,
        Long tripId,
        LocalDate travelStartDate,
        LocalDate travelEndDate,
        LocalDateTime createdAt
) {
    public static CreateGroupResponse of(Group group, Trip trip) {
        return new CreateGroupResponse(
                group.getId(), group.getGroupName(), trip.getRegion(), group.getMaxMembers(),
                group.getMemberCount(), group.getOwner().getId(), group.getInviteCode(), trip.getId(),
                trip.getTravelStartDate(), trip.getTravelEndDate(), group.getCreatedAt());
    }
}
