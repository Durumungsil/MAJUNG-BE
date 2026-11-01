package durumungsil.majung.domain.story.dto.response;

import durumungsil.majung.domain.preference.entity.*;

import java.util.List;

public record NearbyDestinationsResponse(
        BaseDestination baseDestination,
        int radiusMeters,
        TransportType transportType,
        List<Item> items,
        String nextCursor) {
    public record BaseDestination(String placeId, Double latitude, Double longitude) {}

    public record Item(
            String placeId,
            String name,
            TravelCategory category,
            Double latitude,
            Double longitude,
            int distanceMeters,
            double score,
            int estimatedTravelMinutes) {}
}
