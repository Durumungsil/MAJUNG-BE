package durumungsil.majung.domain.story.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/** 여행지 선택 저장 (PUT - 기존 선택을 통째로 교체). 빈 목록이면 선택을 비운다 */
public record SaveDestinationSelectionRequest(
        @NotNull(message = MESSAGE) List<@NotNull(message = MESSAGE) @Valid Item> destinations
) {
    static final String MESSAGE = "방문 순서가 중복되거나 여행 기간을 벗어난 장소가 포함되었습니다.";

    public record Item(
            @NotNull(message = MESSAGE) Long destinationId,
            @NotNull(message = MESSAGE) @Min(value = 1, message = MESSAGE) Integer order,
            @NotNull(message = MESSAGE) LocalDate visitDate
    ) {
    }
}
