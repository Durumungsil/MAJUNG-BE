package durumungsil.majung.domain.roulette.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DrawRouletteRequest(
        @Size(max = 100) @Pattern(regexp = ".*\\S.*") String region,
        @Size(max = 50) @Pattern(regexp = "[A-Z][A-Z0-9_]*") String category,
        @Size(max = 1000) List<@NotNull @Pattern(regexp="[A-Za-z0-9_-]{1,255}") String> excludePlaceIds
) {
    public DrawRouletteRequest {
        excludePlaceIds = excludePlaceIds == null ? List.of() : excludePlaceIds;
    }
}
