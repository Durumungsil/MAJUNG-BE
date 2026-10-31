package durumungsil.majung.domain.roulette.dto.response;

import durumungsil.majung.domain.preference.entity.TravelCategory;
import durumungsil.majung.domain.roulette.entity.RouletteResult;

import java.time.Instant;

public record RoulettePlaceResponse(
        String placeId, TravelCategory category, Double latitude, Double longitude) {
    public static RoulettePlaceResponse of(RouletteResult result) {
        var place = result.getGooglePlace();
        boolean fresh = place.hasFreshLocation(Instant.now());
        return new RoulettePlaceResponse(
                place.getPlaceId(),
                result.getCategory(),
                fresh ? place.getLatitude() : null,
                fresh ? place.getLongitude() : null);
    }
}
