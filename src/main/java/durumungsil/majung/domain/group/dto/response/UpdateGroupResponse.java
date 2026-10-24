package durumungsil.majung.domain.group.dto.response;

import durumungsil.majung.domain.group.entity.Group;
import durumungsil.majung.domain.trip.entity.Trip;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record UpdateGroupResponse(
        Long groupId,
        String groupName,
        String region,
        Integer maxMembers,
        LocalDate travelStartDate,
        LocalDate travelEndDate,
        Long ownerId,
        LocalDateTime updatedAt
) {
    public static UpdateGroupResponse of(Group group, Trip trip) {
        return new UpdateGroupResponse(
                group.getId(), group.getGroupName(),
                trip == null ? null : trip.getRegion(),
                group.getMaxMembers(),
                trip == null ? null : trip.getTravelStartDate(),
                trip == null ? null : trip.getTravelEndDate(),
                group.getOwner().getId(), group.getUpdatedAt());
    }
}
