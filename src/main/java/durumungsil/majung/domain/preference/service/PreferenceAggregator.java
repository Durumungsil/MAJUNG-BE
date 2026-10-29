package durumungsil.majung.domain.preference.service;

import durumungsil.majung.domain.group.entity.Group;
import durumungsil.majung.domain.preference.entity.FoodPreference;
import durumungsil.majung.domain.preference.entity.Preference;
import durumungsil.majung.domain.preference.entity.TransportType;
import durumungsil.majung.domain.preference.entity.TravelCategory;
import durumungsil.majung.domain.preference.dto.response.PreferenceSummaryResponse;
import durumungsil.majung.global.util.EnumJsonArrays;
import durumungsil.majung.global.util.StringJsonArrays;
import org.springframework.stereotype.Component;
import durumungsil.majung.domain.preference.profile.*;
import durumungsil.majung.domain.place.PlaceTypeCatalog;
import durumungsil.majung.domain.story.service.GroupTaste;
import durumungsil.majung.domain.trip.entity.Trip;
import java.util.Comparator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * 취향 통합 규칙
 * - 교통수단·여행 카테고리: 최빈값 (동률이면 모두)
 * - 음식: 음식별 평균 점수가 가장 높은 음식 (동률이면 모두)
 * - 알레르기: 합집합 (자유 입력 문자열, 가나다·알파벳 순)
 * - 예산: min = 멤버 예산 중 최솟값, max = 멤버 예산의 중앙값
 */
@Component
public class PreferenceAggregator {

    /** AI가 수정할 수 없는 수치와 규칙 기반 검색어를 한 번에 만든다. */
    public PreferenceProfileData profile(Trip trip, List<Preference> preferences, long version) {
        var summary=aggregate(trip.getGroup(),preferences);
        var taste=GroupTaste.from(preferences);
        var categories=taste.categoryVotes().entrySet().stream().filter(e->!PlaceTypeCatalog.types(e.getKey()).isEmpty())
                .map(e->new PreferenceProfileData.Category(e.getKey(),taste.ratio(e.getKey())))
                .sorted(Comparator.comparingDouble(PreferenceProfileData.Category::ratio).reversed().thenComparing(PreferenceProfileData.Category::category)).toList();
        Map<String,long[]> totals=new LinkedHashMap<>();
        for (Preference p:preferences) for(FoodPreference food:p.getFoodPreferences()) {
            long[] values=totals.computeIfAbsent(food.getFood(),key->new long[2]); values[0]+=food.getScore(); values[1]++;
        }
        var foods=totals.entrySet().stream().map(e->new PreferenceProfileData.Food(e.getKey(),(double)e.getValue()[0]/e.getValue()[1]))
                .sorted(Comparator.comparingDouble(PreferenceProfileData.Food::score).reversed().thenComparing(PreferenceProfileData.Food::name)).toList();
        List<TransportType> all=preferences.stream().flatMap(p->p.getTransportTypes().stream()).distinct().sorted().toList();
        List<Allergy> allergies=AllergyNormalizer.normalize(summary.allergies());
        TreeSet<String> avoid=new TreeSet<>(AllergyNormalizer.avoidTypes(allergies));
        preferences.stream().flatMap(p->StringJsonArrays.fromJson(p.getExcludedCategories()).stream())
                .map(value->value.toLowerCase(java.util.Locale.ROOT)).filter(PlaceTypeCatalog::allowsAvoidType).forEach(avoid::add);
        List<PreferenceProfileData.SearchQuery> queries=new ArrayList<>();
        for(var category:categories) if(category.category()!=TravelCategory.FOOD)
            queries.add(new PreferenceProfileData.SearchQuery(trip.getRegion()+" "+PlaceTypeCatalog.label(category.category()),category.category(),PlaceTypeCatalog.types(category.category()).getFirst()));
        int meals=categories.stream().anyMatch(c->c.category()==TravelCategory.FOOD)?2:1;
        for(int i=0;i<meals;i++) {
            String food=i<foods.size()?foods.get(i).name():(i==0?"식사 음식점":"한식 음식점");
            queries.add(new PreferenceProfileData.SearchQuery(trip.getRegion()+" "+food,TravelCategory.FOOD,i==0?"restaurant":"korean_restaurant"));
        }
        if(categories.stream().anyMatch(c->c.category()==TravelCategory.FOOD))
            queries.add(new PreferenceProfileData.SearchQuery(trip.getRegion()+" 맛집",TravelCategory.FOOD,"restaurant"));
        Integer meal=summary.budget().max()==null?null:trip.mealBudgetPerPerson(summary.budget().max());
        String description=categories.isEmpty()?"끼니를 포함한 그룹 여행":categories.stream().limit(3)
                .map(c->PlaceTypeCatalog.label(c.category())).collect(java.util.stream.Collectors.joining("·"))+" 선호 그룹";
        return new PreferenceProfileData(version,GeneratedBy.RULE,description,
                new PreferenceProfileData.Transport(taste.primaryTransportOr(TransportType.BUS),all),
                new PreferenceProfileData.Budget(summary.budget().min(),summary.budget().max(),meal),categories,foods,
                new PreferenceProfileData.Allergies(allergies),List.copyOf(avoid),queries);
    }

    public PreferenceSummaryResponse aggregate(Group group, List<Preference> preferences) {
        List<TransportType> transportTypes = mostFrequent(TransportType.class,
                preferences.stream().map(Preference::getTransportTypes).toList());
        List<TravelCategory> categories = mostFrequent(TravelCategory.class,
                preferences.stream()
                        .map(p -> EnumJsonArrays.fromJson(p.getTravelCategories(), TravelCategory.class))
                        .toList());

        TreeSet<String> allergies = new TreeSet<>();
        preferences.forEach(p -> allergies.addAll(StringJsonArrays.fromJson(p.getAllergies())));

        return new PreferenceSummaryResponse(
                group.getId(),
                transportTypes,
                highestAverageFoods(preferences),
                categories,
                budget(preferences),
                new ArrayList<>(allergies),
                preferences.size(),
                group.getMemberCount());
    }

    /** 멤버별 선택 목록에서 가장 많이 선택된 값들 (enum 선언 순서로 정렬) */
    private static <E extends Enum<E>> List<E> mostFrequent(Class<E> type, List<? extends Collection<E>> selections) {
        Map<E, Integer> counts = new EnumMap<>(type);
        for (Collection<E> selection : selections) {
            EnumSet<E> distinct = selection.isEmpty() ? EnumSet.noneOf(type) : EnumSet.copyOf(selection);
            distinct.forEach(value -> counts.merge(value, 1, Integer::sum));
        }
        int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() == max)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** 음식별 평균 점수가 가장 높은 음식들 (먼저 나온 순서 유지) */
    private static List<String> highestAverageFoods(List<Preference> preferences) {
        Map<String, long[]> sumAndCount = new LinkedHashMap<>(); // food -> [점수 합, 인원]
        for (Preference preference : preferences) {
            for (FoodPreference food : preference.getFoodPreferences()) {
                long[] acc = sumAndCount.computeIfAbsent(food.getFood(), key -> new long[2]);
                acc[0] += food.getScore();
                acc[1] += 1;
            }
        }

        List<String> best = new ArrayList<>();
        long[] bestAcc = null;
        for (Map.Entry<String, long[]> entry : sumAndCount.entrySet()) {
            long[] acc = entry.getValue();
            // 평균 비교를 나눗셈 없이: a/b vs c/d  ->  a*d vs c*b
            int compare = bestAcc == null ? 1 : Long.compare(acc[0] * bestAcc[1], bestAcc[0] * acc[1]);
            if (compare > 0) {
                best.clear();
                best.add(entry.getKey());
                bestAcc = acc;
            } else if (compare == 0) {
                best.add(entry.getKey());
            }
        }
        return best;
    }

    private static PreferenceSummaryResponse.Budget budget(List<Preference> preferences) {
        List<Integer> budgets = preferences.stream()
                .map(Preference::getBudgetMax)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        if (budgets.isEmpty()) {
            return new PreferenceSummaryResponse.Budget(null, null, PreferenceSummaryResponse.Budget.MEDIAN);
        }
        int size = budgets.size();
        int median = size % 2 == 1
                ? budgets.get(size / 2)
                : (int) (((long) budgets.get(size / 2 - 1) + budgets.get(size / 2)) / 2);
        return new PreferenceSummaryResponse.Budget(budgets.get(0), median, PreferenceSummaryResponse.Budget.MEDIAN);
    }
}
