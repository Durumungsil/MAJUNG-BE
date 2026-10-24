package durumungsil.majung.domain.trip.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateTripRequest(
        @NotBlank(message = MESSAGE) String region,
        @NotNull(message = MESSAGE) LocalDate travelStartDate,
        @NotNull(message = MESSAGE) LocalDate travelEndDate
) {
    static final String MESSAGE = "지역 또는 여행 기간이 올바르지 않습니다.";
}
