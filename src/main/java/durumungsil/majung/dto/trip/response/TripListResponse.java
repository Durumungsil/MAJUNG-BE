package durumungsil.majung.dto.trip.response;

import durumungsil.majung.domain.trip.Trip;
import durumungsil.majung.domain.trip.TripMode;
import durumungsil.majung.domain.trip.TripStatus;

import java.time.LocalDate;
import java.util.List;

public record TripListResponse(Long groupId, List<Item> items) {

    public static TripListResponse of(Long groupId, List<Trip> trips) {
        return new TripListResponse(groupId, trips.stream().map(Item::from).toList());
    }

    public record Item(
            Long tripId,
            String region,
            TripMode mode,
            TripStatus status,
            LocalDate travelStartDate,
            LocalDate travelEndDate
    ) {
        static Item from(Trip trip) {
            return new Item(trip.getId(), trip.getRegion(), trip.getMode(), trip.getStatus(),
                    trip.getTravelStartDate(), trip.getTravelEndDate());
        }
    }
}
