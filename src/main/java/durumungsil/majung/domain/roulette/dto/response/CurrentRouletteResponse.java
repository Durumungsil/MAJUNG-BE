package durumungsil.majung.domain.roulette.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

public record CurrentRouletteResponse(
        Long groupId,
        Long tripId,
        @JsonInclude(JsonInclude.Include.ALWAYS) RouletteResultResponse destination,
        @JsonInclude(JsonInclude.Include.ALWAYS) RouletteResultResponse restaurant) {}
