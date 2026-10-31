package durumungsil.majung.domain.story.dto.request;

import durumungsil.majung.domain.preference.entity.TravelCategory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** 여행지 선택 저장 (PUT - 기존 선택을 통째로 교체). 빈 목록이면 선택을 비운다 */
public record SaveDestinationSelectionRequest(
        @NotNull(message = MESSAGE) @Size(max = 50)
                List<@NotNull(message = MESSAGE) @Valid Item> destinations) {
    static final String MESSAGE = "방문 순서가 중복되거나 여행 기간을 벗어난 장소가 포함되었습니다.";

    public record Item(
            @NotNull(message = MESSAGE) @Pattern(regexp = "[A-Za-z0-9_-]{1,255}", message = MESSAGE)
                    String placeId,
            @NotNull(message = MESSAGE) @Min(value = 1, message = MESSAGE) Integer order,
            @NotNull(message = MESSAGE) LocalDate visitDate,
            TravelCategory category) {}
}
