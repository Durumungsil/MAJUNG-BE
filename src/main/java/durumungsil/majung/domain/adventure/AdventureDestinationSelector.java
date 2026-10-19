package durumungsil.majung.domain.adventure;

import durumungsil.majung.domain.group.GroupMemberRepository;
import durumungsil.majung.domain.preference.Preference;
import durumungsil.majung.domain.preference.PreferenceRepository;
import durumungsil.majung.domain.store.Store;
import durumungsil.majung.domain.store.StoreRepository;
import durumungsil.majung.domain.store.Type;
import durumungsil.majung.domain.trip.Trip;
import durumungsil.majung.domain.trip.TripConstraintRepository;
import durumungsil.majung.global.api.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

@Component
@RequiredArgsConstructor
public class AdventureDestinationSelector {
    private static final String UNSATISFIED = "그룹 최소 조건을 만족하는 커리큘럼을 생성할 수 없습니다.";
    private static final Map<String, List<String>> REGION_ALIASES = Map.of(
            "BUSAN", List.of("BUSAN", "부산", "부산광역시"),
            "SEOUL", List.of("SEOUL", "서울", "서울특별시"),
            "GANGNEUNG", List.of("GANGNEUNG", "강릉", "강릉시"),
            "JEJU", List.of("JEJU", "제주", "제주특별자치도"));

    private final StoreRepository stores;
    private final PreferenceRepository preferences;
    private final TripConstraintRepository constraints;
    private final GroupMemberRepository members;
    private final ObjectMapper objectMapper;

    public List<Store> select(Trip trip, int count) {
        Long groupId = trip.getGroup().getId();
        List<Preference> opinions = preferences.findCurrentMemberPreferences(trip.getId(), groupId);
        long memberCount = members.countByGroupId(groupId);
        if (memberCount == 0 || opinions.size() != memberCount) {
            throw ApiException.conflict(UNSATISFIED);
        }
        Set<String> allergies = new HashSet<>();
        Set<String> excluded = new HashSet<>();
        int budget = Integer.MAX_VALUE;
        for (Preference opinion : opinions) {
            allergies.addAll(readArray(opinion.getAllergies()));
            if (opinion.getBudgetMax() != null) {
                if (opinion.getBudgetMax() < 0) { throw ApiException.conflict(UNSATISFIED); }
                budget = Math.min(budget, trip.mealBudgetPerPerson(opinion.getBudgetMax()));
            }
        }
        constraints.findById(trip.getId()).ifPresent(constraint -> {
            allergies.addAll(readArray(constraint.getAllergies()));
            excluded.addAll(readArray(constraint.getExcludedCategories()));
        });
        Set<Type> excludedTypes = new HashSet<>();
        for (String category : excluded) {
            Type type = Arrays.stream(Type.values())
                    .filter(value -> value.name().equalsIgnoreCase(category) || value.getCode().equals(category)
                            || value.getDisplayName().equals(category))
                    .findFirst().orElseThrow(() -> ApiException.conflict(UNSATISFIED));
            excludedTypes.add(type);
        }
        if (trip.getRegion() == null || trip.getRegion().isBlank()) { throw ApiException.conflict(UNSATISFIED); }
        String region = trip.getRegion().trim();
        List<String> aliases = REGION_ALIASES.values().stream().filter(values -> values.contains(region))
                .findFirst().orElse(List.of(region));
        int mealBudget = budget;
        List<Store> candidates = new ArrayList<>(stores.findInRegions(aliases).stream()
                .filter(store -> store.getDestinationType() != null && !excludedTypes.contains(store.getDestinationType()))
                // 장소별 원재료 데이터가 없으므로 알레르기가 있으면 음식 장소를 선택하지 않는다.
                .filter(store -> allergies.isEmpty() || store.getDestinationType() != Type.FOOD)
                .filter(store -> store.getDestinationType() != Type.FOOD || store.isWithinBudget(mealBudget))
                .toList());
        if (candidates.size() < count) { throw ApiException.conflict(UNSATISFIED); }
        Collections.shuffle(candidates);
        return List.copyOf(candidates.subList(0, count));
    }

    private Set<String> readArray(String json) {
        if (json == null || json.isBlank()) { return Set.of(); }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) { throw ApiException.conflict(UNSATISFIED); }
            Set<String> values = new HashSet<>();
            for (JsonNode entry : node) {
                if (!entry.isString() || entry.asString().isBlank()) { throw ApiException.conflict(UNSATISFIED); }
                values.add(entry.asString());
            }
            return values;
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw ApiException.conflict(UNSATISFIED);
        }
    }
}
