package durumungsil.majung.domain.preference.dto.response;

import durumungsil.majung.domain.preference.entity.Preference;
import durumungsil.majung.domain.preference.entity.TransportType;
import durumungsil.majung.domain.preference.entity.TravelCategory;
import durumungsil.majung.global.util.EnumJsonArrays;
import durumungsil.majung.global.util.StringJsonArrays;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * loaded_from_previous - 현재 회차에 입력이 없어 이전 회차 취향을 불러왔으면 true.
 * budget.max 는 예산을 입력하지 않았으면 null.
 */
public record MyPreferenceResponse(
        Long userId,
        List<TransportType> transportTypes,
        List<FoodPreferenceItem> foodPreferences,
        List<String> allergies,
        List<TravelCategory> travelCategories,
        Budget budget,
        boolean loadedFromPrevious,
        LocalDateTime updatedAt
) {
    public static MyPreferenceResponse of(Preference preference, boolean loadedFromPrevious) {
        return new MyPreferenceResponse(
                preference.getUser().getId(),
                preference.getTransportTypes().stream().sorted(Comparator.naturalOrder()).toList(),
                preference.getFoodPreferences().stream()
                        .map(food -> new FoodPreferenceItem(food.getFood(), food.getScore()))
                        .toList(),
                StringJsonArrays.fromJson(preference.getAllergies()),
                EnumJsonArrays.fromJson(preference.getTravelCategories(), TravelCategory.class),
                new Budget(preference.getBudgetMax()),
                loadedFromPrevious,
                preference.getUpdatedAt());
    }

    public record FoodPreferenceItem(String food, Integer score) {
    }

    public record Budget(Integer max) {
    }
}
