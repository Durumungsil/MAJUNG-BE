package durumungsil.majung.domain.roulette.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import durumungsil.majung.domain.roulette.entity.RouletteType;

import java.time.Instant;
import java.util.List;

public record DrawRouletteResponse(Long rouletteId, RouletteType type, RoulettePlaceResponse place,
                                    @JsonInclude(JsonInclude.Include.NON_NULL) List<String> groupAllergies,
                                    int round, Instant drawnAt) {
}
