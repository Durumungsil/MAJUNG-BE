package durumungsil.majung.domain.roulette.dto.response;

import durumungsil.majung.domain.roulette.entity.RouletteResult;
import durumungsil.majung.domain.roulette.entity.RouletteType;

import java.time.Instant;

public record RouletteResultResponse(Long rouletteId, Long groupId, RouletteType type,
                                      RoulettePlaceResponse place, Instant drawnAt) {
    public static RouletteResultResponse of(RouletteResult result) {
        return new RouletteResultResponse(result.getId(), result.getTrip().getGroup().getId(),
                result.getType(), RoulettePlaceResponse.of(result.getStore()), result.getDrawnAt());
    }
}
