package durumungsil.majung.domain.group.dto.response;

import durumungsil.majung.domain.trip.entity.Trip;
import durumungsil.majung.domain.trip.entity.TripMode;

import java.time.LocalDateTime;

public record SelectModeResponse(Long groupId, Long tripId, TripMode mode, LocalDateTime updatedAt) {

    public static SelectModeResponse of(Long groupId, Trip trip) {
        return new SelectModeResponse(groupId, trip.getId(), trip.getMode(), trip.getUpdatedAt());
    }
}
