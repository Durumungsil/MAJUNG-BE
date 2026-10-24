package durumungsil.majung.domain.adventure.service;

import durumungsil.majung.domain.group.repository.GroupMemberRepository;
import durumungsil.majung.domain.preference.entity.Preference;
import durumungsil.majung.domain.preference.repository.PreferenceRepository;
import durumungsil.majung.domain.store.entity.Store;
import durumungsil.majung.domain.store.repository.StoreRepository;
import durumungsil.majung.domain.store.entity.Type;
import durumungsil.majung.domain.trip.entity.Trip;
import durumungsil.majung.domain.trip.repository.TripConstraintRepository;
import durumungsil.majung.global.exception.BusinessException;
import durumungsil.majung.global.exception.ErrorCode;
import durumungsil.majung.global.util.RegionAliases;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

@Component
@RequiredArgsConstructor
public class AdventureDestinationSelector {

    private final StoreRepository stores;
    private final PreferenceRepository preferences;
    private final TripConstraintRepository constraints;
    private final GroupMemberRepository members;
    private final ObjectMapper objectMapper;

    public List<Store> select(Trip trip, int count) {
        Long groupId = trip.getGroup().getId();
        List<Preference> opinions = preferences.findAllOfCurrentMembers(trip.getId(), groupId);
        long memberCount = members.countByGroupId(groupId);
        if (memberCount == 0 || opinions.size() != memberCount) {
            throw unsatisfied();
        }
        Set<String> allergies = new HashSet<>();
        Set<String> excluded = new HashSet<>();
        int budget = Integer.MAX_VALUE;
        for (Preference opinion : opinions) {
            allergies.addAll(readArray(opinion.getAllergies()));
            if (opinion.getBudgetMax() != null) {
                if (opinion.getBudgetMax() < 0) { throw unsatisfied(); }
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
                    .findFirst().orElseThrow(() -> unsatisfied());
            excludedTypes.add(type);
        }
        if (trip.getRegion() == null || trip.getRegion().isBlank()) { throw unsatisfied(); }
        String region = trip.getRegion().trim();
        List<String> aliases = RegionAliases.of(region);
        int mealBudget = budget;
        // 제외 업종과 (알레르기가 있으면) 음식 장소는 DB 에서 거른 뒤 후보 수를 자른다
        Set<Type> allowedTypes = EnumSet.allOf(Type.class);
        allowedTypes.removeAll(excludedTypes);
        if (!allergies.isEmpty()) {
            allowedTypes.remove(Type.FOOD);
        }
        if (allowedTypes.isEmpty()) { throw unsatisfied(); }
        List<Store> candidates = new ArrayList<>(stores.findInRegionsAndTypes(
                        aliases, allowedTypes, StoreRepository.regionCandidateLimit()).stream()
                .filter(store -> store.getDestinationType() != null && !excludedTypes.contains(store.getDestinationType()))
                // 장소별 원재료 데이터가 없으므로 알레르기가 있으면 음식 장소를 선택하지 않는다.
                .filter(store -> allergies.isEmpty() || store.getDestinationType() != Type.FOOD)
                .filter(store -> store.getDestinationType() != Type.FOOD || store.isWithinBudget(mealBudget))
                .toList());
        if (candidates.size() < count) { throw unsatisfied(); }
        Collections.shuffle(candidates);
        return List.copyOf(candidates.subList(0, count));
    }

    private Set<String> readArray(String json) {
        if (json == null || json.isBlank()) { return Set.of(); }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) { throw unsatisfied(); }
            Set<String> values = new HashSet<>();
            for (JsonNode entry : node) {
                if (!entry.isString() || entry.asString().isBlank()) { throw unsatisfied(); }
                values.add(entry.asString());
            }
            return values;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw unsatisfied();
        }
    }

    private static BusinessException unsatisfied() {
        return new BusinessException(ErrorCode.ADVENTURE_CONDITION_UNSATISFIED);
    }
}
