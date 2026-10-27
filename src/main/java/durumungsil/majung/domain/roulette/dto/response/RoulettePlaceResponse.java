package durumungsil.majung.domain.roulette.dto.response;

import durumungsil.majung.domain.roulette.service.RouletteCategories;
import durumungsil.majung.domain.store.entity.Store;

public record RoulettePlaceResponse(Long placeId, String name, String category, String address,
                                     Double latitude, Double longitude) {
    public static RoulettePlaceResponse of(Store store) {
        return new RoulettePlaceResponse(store.getDestinationId(), store.getDestinationName(),
                RouletteCategories.of(store), store.getAddress(), store.getLatitude(), store.getLongitude());
    }
}
