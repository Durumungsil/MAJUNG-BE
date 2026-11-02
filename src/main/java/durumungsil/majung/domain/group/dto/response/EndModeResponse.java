package durumungsil.majung.domain.group.dto.response;

import durumungsil.majung.domain.trip.entity.Trip;
import durumungsil.majung.domain.trip.entity.TripMode;
import durumungsil.majung.domain.trip.entity.TripStatus;

import java.time.LocalDateTime;

public record EndModeResponse(Long groupId, Long tripId, TripMode mode, TripStatus status, LocalDateTime endedAt) {

    public static EndModeResponse of(Long groupId, Trip trip) {
        return new EndModeResponse(groupId, trip.getId(), trip.getMode(), trip.getStatus(), trip.getUpdatedAt());
    }
}
