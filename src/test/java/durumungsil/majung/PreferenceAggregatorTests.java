package durumungsil.majung;

import durumungsil.majung.domain.group.entity.Group;
import durumungsil.majung.domain.preference.entity.FoodPreference;
import durumungsil.majung.domain.preference.entity.Preference;
import durumungsil.majung.domain.preference.entity.TransportType;
import durumungsil.majung.domain.preference.entity.TravelCategory;
import durumungsil.majung.domain.preference.dto.response.PreferenceSummaryResponse;
import durumungsil.majung.global.util.EnumJsonArrays;
import durumungsil.majung.global.util.StringJsonArrays;
import durumungsil.majung.domain.preference.service.PreferenceAggregator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PreferenceAggregatorTests {

    private final PreferenceAggregator aggregator = new PreferenceAggregator();

    @Test
    void aggregatesByModeHighestAverageUnionAndMedian() {
        Group group = Group.builder().groupName("g").maxMembers(4).memberCount(3).build();
        List<Preference> preferences = List.of(
                preference(Set.of(TransportType.BUS, TransportType.WALK),
                        List.of(new FoodPreference("pizza", 5), new FoodPreference("pasta", 3)),
                        List.of("PEANUT", "땅콩"), List.of(TravelCategory.FOOD), 100_000),
                preference(Set.of(TransportType.BUS),
                        List.of(new FoodPreference("pizza", 4), new FoodPreference("pasta", 5)),
                        List.of("MILK", "땅콩"), List.of(TravelCategory.FOOD, TravelCategory.CULTURE), 300_000),
                preference(Set.of(TransportType.TAXI),
                        List.of(new FoodPreference("sushi", 4)),
                        List.of(), List.of(TravelCategory.CULTURE), 200_000));

        PreferenceSummaryResponse summary = aggregator.aggregate(group, preferences);

        assertEquals(List.of(TransportType.BUS), summary.transportTypes());
        // pizza 평균 4.5, pasta 4.0, sushi 4.0
        assertEquals(List.of("pizza"), summary.foodPreferences());
        // FOOD 2표, CULTURE 2표 -> 동률이면 모두 (enum 선언 = 설문 순서)
        assertEquals(List.of(TravelCategory.CULTURE, TravelCategory.FOOD), summary.travelCategories());
        // 자유 입력 알레르기는 합집합, 중복 제거, 정렬
        assertEquals(List.of("MILK", "PEANUT", "땅콩"), summary.allergies());
        assertEquals(100_000, summary.budget().min());
        assertEquals(200_000, summary.budget().max());
        assertEquals(3, summary.completedMemberCount());
        assertEquals(3, summary.memberCount());
    }

    @Test
    void medianOfEvenCountIsAverageOfMiddleTwo() {
        Group group = Group.builder().groupName("g").maxMembers(4).memberCount(2).build();
        List<Preference> preferences = List.of(
                preference(Set.of(TransportType.BUS), List.of(), List.of(), List.of(TravelCategory.FOOD), 150_000),
                preference(Set.of(TransportType.BUS), List.of(), List.of(), List.of(TravelCategory.FOOD), 250_000));

        PreferenceSummaryResponse summary = aggregator.aggregate(group, preferences);

        assertEquals(150_000, summary.budget().min());
        assertEquals(200_000, summary.budget().max());
        assertEquals(List.of(), summary.foodPreferences());
    }

    @Test
    void membersWithoutBudgetAreSkipped() {
        Group group = Group.builder().groupName("g").maxMembers(4).memberCount(3).build();
        List<Preference> preferences = List.of(
                preference(Set.of(TransportType.BUS), List.of(), List.of(), List.of(TravelCategory.HEALING), null),
                preference(Set.of(TransportType.BUS), List.of(), List.of(), List.of(TravelCategory.HEALING), 120_000));

        PreferenceSummaryResponse summary = aggregator.aggregate(group, preferences);

        assertEquals(120_000, summary.budget().min());
        assertEquals(120_000, summary.budget().max());

        PreferenceSummaryResponse nobody = aggregator.aggregate(group, List.of(preferences.get(0)));
        assertEquals(null, nobody.budget().min());
        assertEquals(null, nobody.budget().max());
    }

    private static Preference preference(Set<TransportType> transportTypes, List<FoodPreference> foods,
                                         List<String> allergies, List<TravelCategory> categories, Integer budgetMax) {
        return Preference.builder()
                .transportTypes(transportTypes)
                .foodPreferences(foods)
                .allergies(StringJsonArrays.toJson(allergies))
                .travelCategories(EnumJsonArrays.toJson(categories))
                .budgetMax(budgetMax)
                .build();
    }
}
