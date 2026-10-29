package durumungsil.majung.domain.adventure.service;

import durumungsil.majung.domain.group.repository.GroupMemberRepository;
import durumungsil.majung.domain.preference.entity.Preference;
import durumungsil.majung.domain.preference.profile.AllergyNormalizer;
import durumungsil.majung.domain.preference.repository.PreferenceRepository;
import durumungsil.majung.domain.store.entity.Store;
import durumungsil.majung.domain.store.entity.Type;
import durumungsil.majung.domain.store.repository.StoreRepository;
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
        List<Store> candidates = candidates(trip);
        if (candidates.size() < count) throw unsatisfied();
        Collections.shuffle(candidates);
        int[] remaining = {20_000};
        for (Store start : candidates) {
            if (remaining[0] <= 0) break;
            List<Store> path = new ArrayList<>();
            path.add(start);
            if (extend(path, candidates, count, remaining)) return List.copyOf(path);
        }
        throw unsatisfied();
    }

    private List<Store> candidates(Trip trip) {
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
                if (opinion.getBudgetMax() < 0) {
                    throw unsatisfied();
                }
                budget = Math.min(budget, trip.mealBudgetPerPerson(opinion.getBudgetMax()));
            }
        }
        constraints
                .findById(trip.getId())
                .ifPresent(
                        constraint -> {
                            allergies.addAll(readArray(constraint.getAllergies()));
                            excluded.addAll(readArray(constraint.getExcludedCategories()));
                        });
        Set<Type> excludedTypes = new HashSet<>();
        for (String category : excluded) {
            Type type =
                    Arrays.stream(Type.values())
                            .filter(
                                    value ->
                                            value.name().equalsIgnoreCase(category)
                                                    || value.getCode().equals(category)
                                                    || value.getDisplayName().equals(category))
                            .findFirst()
                            .orElseThrow(() -> unsatisfied());
            excludedTypes.add(type);
        }
        if (trip.getRegion() == null || trip.getRegion().isBlank()) {
            throw unsatisfied();
        }
        String region = trip.getRegion().trim();
        List<String> aliases = RegionAliases.of(region);
        int mealBudget = budget;
        // 모험은 숙박·전문 기술 업종을 항상 제외한다.
        Set<Type> allowedTypes = EnumSet.allOf(Type.class);
        allowedTypes.removeAll(excludedTypes);
        allowedTypes.removeAll(Set.of(Type.LODGING, Type.SCIENCE_TECHNOLOGY));
        var normalizedAllergies = AllergyNormalizer.normalize(allergies);
        if (allowedTypes.isEmpty()) {
            throw unsatisfied();
        }
        List<Store> candidates =
                new ArrayList<>(
                        stores
                                .findInRegionsAndTypes(
                                        aliases,
                                        allowedTypes,
                                        StoreRepository.regionCandidateLimit())
                                .stream()
                                .filter(
                                        store ->
                                                store.getDestinationType() != null
                                                        && allowedTypes.contains(
                                                                store.getDestinationType()))
                                .filter(
                                        store ->
                                                StoreAllergyFilter.allows(
                                                        store, normalizedAllergies))
                                .filter(
                                        store ->
                                                store.getDestinationType() != Type.FOOD
                                                        || store.isWithinBudget(mealBudget))
                                .toList());
        return candidates;
    }

    static final double MAX_WALKING_LEG_METERS = 2_000;

    private boolean extend(List<Store> path, List<Store> candidates, int count, int[] remaining) {
        if (path.size() == count) return true;
        if (--remaining[0] <= 0) return false;
        Store last = path.getLast();
        for (Store next : candidates) {
            if (path.contains(next) || distance(last, next) > MAX_WALKING_LEG_METERS) continue;
            path.add(next);
            if (extend(path, candidates, count, remaining)) return true;
            path.removeLast();
        }
        return false;
    }

    public Store replacement(Trip trip, Set<Long> excluded, Store previous, Store next) {
        // 동일한 취향·예산 필터를 적용한 후보에서 앞뒤 경로가 이어지는 장소를 고른다.
        List<Store> candidates = candidates(trip);
        Collections.shuffle(candidates);
        return candidates.stream()
                .filter(candidate -> !excluded.contains(candidate.getDestinationId()))
                .filter(
                        candidate ->
                                previous == null
                                        || distance(previous, candidate) <= MAX_WALKING_LEG_METERS)
                .filter(
                        candidate ->
                                next == null || distance(next, candidate) <= MAX_WALKING_LEG_METERS)
                .findFirst()
                .orElseThrow(AdventureDestinationSelector::unsatisfied);
    }

    private static double distance(Store a, Store b) {
        return a.distanceMetersFrom(b.getLatitude(), b.getLongitude());
    }

    private Set<String> readArray(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) {
                throw unsatisfied();
            }
            Set<String> values = new HashSet<>();
            for (JsonNode entry : node) {
                if (!entry.isString() || entry.asString().isBlank()) {
                    throw unsatisfied();
                }
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
