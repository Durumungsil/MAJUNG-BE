package durumungsil.majung.domain.story.dto.response;

import java.time.LocalDateTime;

public record SaveDestinationSelectionResponse(
        Long groupId,
        int destinationCount,
        LocalDateTime updatedAt
) {
}
