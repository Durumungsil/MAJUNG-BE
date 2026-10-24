package durumungsil.majung.domain.preference.dto.response;

import java.time.LocalDateTime;

public record SavePreferenceResponse(
        Long groupId,
        Long userId,
        boolean preferencesCompleted,
        LocalDateTime updatedAt
) {
}
