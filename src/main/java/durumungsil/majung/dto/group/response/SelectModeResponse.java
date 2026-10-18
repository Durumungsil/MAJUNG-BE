package durumungsil.majung.dto.group.response;

import durumungsil.majung.domain.trip.Trip;
import durumungsil.majung.domain.trip.TripMode;

import java.time.LocalDateTime;

public record SelectModeResponse(Long groupId, Long tripId, TripMode mode, LocalDateTime updatedAt) {

    public static SelectModeResponse of(Long groupId, Trip trip) {
        return new SelectModeResponse(groupId, trip.getId(), trip.getMode(), trip.getUpdatedAt());
    }
}
