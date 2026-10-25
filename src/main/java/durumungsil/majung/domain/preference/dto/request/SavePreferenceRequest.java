package durumungsil.majung.domain.preference.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 그룹 의견 수집 (PUT - 기존 취향을 통째로 교체).
 * 교통수단·여행 스타일은 문자열로 받아 서비스에서 enum 으로 변환한다 (모르는 값이면 400).
 * 알레르기는 자유 입력 문자열 목록이다 (서비스에서 공백·중복 정리, 개수·길이 제한).
 * 예산은 선택 입력이다 (budget 을 빼거나 max 를 null 로 보내면 예산 없음).
 */
public record SavePreferenceRequest(
        @NotEmpty(message = MESSAGE) List<String> transportTypes,
        @NotNull(message = MESSAGE) List<@NotNull(message = MESSAGE) @Valid FoodPreferenceItem> foodPreferences,
        @NotNull(message = MESSAGE) List<String> allergies,
        @NotEmpty(message = MESSAGE) List<String> travelCategories,
        @Valid Budget budget
) {
    static final String MESSAGE = "알 수 없는 카테고리이거나 알레르기·예산 값이 올바르지 않습니다.";

    public record FoodPreferenceItem(
            @NotNull(message = MESSAGE) String food,
            @NotNull(message = MESSAGE) @Min(value = 1, message = MESSAGE) @Max(value = 5, message = MESSAGE) Integer score
    ) {
    }

    public record Budget(
            @Min(value = 0, message = MESSAGE) @Max(value = 100_000_000, message = MESSAGE) Integer max
    ) {
    }
}
