package durumungsil.majung.domain.roulette.dto.response;

import java.time.Instant;
import java.util.List;

public record MinimumConstraintsResponse(Long groupId, List<String> allergies, List<String> excludedCategories,
                                         int sourceMemberCount, Instant updatedAt) {
}
