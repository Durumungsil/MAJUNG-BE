package durumungsil.majung.dto.trip.response;

import durumungsil.majung.domain.trip.Trip;
import durumungsil.majung.domain.trip.TripMode;
import durumungsil.majung.domain.trip.TripStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TripResponse(
        Long tripId,
        Long groupId,
        String region,
        TripMode mode,
        TripStatus status,
        LocalDate travelStartDate,
        LocalDate travelEndDate,
        LocalDateTime createdAt
) {
    public static TripResponse from(Trip trip) {
        return new TripResponse(trip.getId(), trip.getGroup().getId(), trip.getRegion(), trip.getMode(),
                trip.getStatus(), trip.getTravelStartDate(), trip.getTravelEndDate(), trip.getCreatedAt());
    }
}
