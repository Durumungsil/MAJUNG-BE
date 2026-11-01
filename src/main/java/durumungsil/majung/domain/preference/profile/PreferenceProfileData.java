package durumungsil.majung.domain.preference.profile;

import durumungsil.majung.domain.preference.entity.*;

import java.util.List;

public record PreferenceProfileData(
        long version,
        GeneratedBy generatedBy,
        String summary,
        Transport transport,
        Budget budget,
        List<Category> travelCategories,
        List<Food> foods,
        Allergies allergies,
        List<String> avoidPlaceTypes,
        List<SearchQuery> searchQueries) {
    public PreferenceProfileData {
        travelCategories = List.copyOf(travelCategories);
        foods = List.copyOf(foods);
        avoidPlaceTypes = List.copyOf(avoidPlaceTypes);
        searchQueries = List.copyOf(searchQueries);
    }

    public record Transport(TransportType primary, List<TransportType> all) {
        public Transport {
            all = List.copyOf(all);
        }
    }

    public record Budget(Integer minPerPerson, Integer medianPerPerson, Integer mealPerPerson) {}

    public record Category(TravelCategory category, double ratio) {}

    public record Food(String name, double score) {}

    public record Allergies(List<Allergy> normalized) {
        public Allergies {
            normalized = List.copyOf(normalized);
        }
    }

    public record SearchQuery(String text, TravelCategory category, String includedType) {}

    public PreferenceProfileData enriched(
            String summary,
            List<Allergy> allergies,
            List<String> avoid,
            List<SearchQuery> queries) {
        return new PreferenceProfileData(
                version,
                GeneratedBy.AI,
                summary,
                transport,
                budget,
                travelCategories,
                foods,
                new Allergies(allergies),
                avoid,
                queries);
    }
}
