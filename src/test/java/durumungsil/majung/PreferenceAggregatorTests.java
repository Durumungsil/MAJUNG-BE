package durumungsil.majung;

import durumungsil.majung.domain.group.Group;
import durumungsil.majung.domain.preference.Allergy;
import durumungsil.majung.domain.preference.FoodPreference;
import durumungsil.majung.domain.preference.Preference;
import durumungsil.majung.domain.preference.TransportType;
import durumungsil.majung.domain.preference.TravelCategory;
import durumungsil.majung.dto.preference.response.PreferenceSummaryResponse;
import durumungsil.majung.global.util.EnumJsonArrays;
import durumungsil.majung.service.PreferenceAggregator;
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
                        List.of(Allergy.PEANUT), List.of(TravelCategory.FOOD), 100_000),
                preference(Set.of(TransportType.BUS),
                        List.of(new FoodPreference("pizza", 4), new FoodPreference("pasta", 5)),
                        List.of(Allergy.MILK), List.of(TravelCategory.FOOD, TravelCategory.CULTURE), 300_000),
                preference(Set.of(TransportType.TAXI),
                        List.of(new FoodPreference("sushi", 4)),
                        List.of(), List.of(TravelCategory.CULTURE), 200_000));

        PreferenceSummaryResponse summary = aggregator.aggregate(group, preferences);

        assertEquals(List.of(TransportType.BUS), summary.transportTypes());
        // pizza 평균 4.5, pasta 4.0, sushi 4.0
        assertEquals(List.of("pizza"), summary.foodPreferences());
        // FOOD 2표, CULTURE 2표 -> 동률이면 모두
        assertEquals(List.of(TravelCategory.FOOD, TravelCategory.CULTURE), summary.travelCategories());
        assertEquals(List.of(Allergy.MILK, Allergy.PEANUT), summary.allergies());
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

    private static Preference preference(Set<TransportType> transportTypes, List<FoodPreference> foods,
                                         List<Allergy> allergies, List<TravelCategory> categories, int budgetMax) {
        return Preference.builder()
                .transportTypes(transportTypes)
                .foodPreferences(foods)
                .allergies(EnumJsonArrays.toJson(allergies))
                .travelCategories(EnumJsonArrays.toJson(categories))
                .budgetMax(budgetMax)
                .build();
    }
}
