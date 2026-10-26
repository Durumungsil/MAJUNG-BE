package durumungsil.majung.domain.preference.service;

import durumungsil.majung.domain.group.service.GroupReader;
import durumungsil.majung.domain.group.entity.Group;
import durumungsil.majung.domain.preference.entity.FoodPreference;
import durumungsil.majung.domain.preference.entity.Preference;
import durumungsil.majung.domain.preference.entity.TransportType;
import durumungsil.majung.domain.preference.entity.TravelCategory;
import durumungsil.majung.domain.trip.entity.Trip;
import durumungsil.majung.domain.user.entity.User;
import durumungsil.majung.domain.preference.dto.request.SavePreferenceRequest;
import durumungsil.majung.domain.preference.dto.response.MyPreferenceResponse;
import durumungsil.majung.domain.preference.dto.response.PreferenceSummaryResponse;
import durumungsil.majung.domain.preference.dto.response.SavePreferenceResponse;
import durumungsil.majung.global.exception.BusinessException;
import durumungsil.majung.global.exception.ErrorCode;
import durumungsil.majung.global.util.EnumJsonArrays;
import durumungsil.majung.global.util.StringJsonArrays;
import durumungsil.majung.domain.preference.repository.PreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PreferenceService {

    /** 알레르기 자유 입력 제한 */
    static final int MAX_ALLERGIES = 20;
    static final int MAX_ALLERGY_LENGTH = 30;

    private final GroupReader groupReader;
    private final PreferenceRepository preferenceRepository;
    private final PreferenceAggregator preferenceAggregator;

    /** 그룹 의견 수집 - 진행 중인 여행 회차에 내 취향을 저장(없으면 생성, 있으면 통째로 교체) */
    @Transactional
    public SavePreferenceResponse savePreference(Long userId, Long groupId, SavePreferenceRequest request) {
        groupReader.getMemberGroupForUpdate(groupId, userId);
        Trip trip = groupReader.getActiveTrip(groupId);

        Set<TransportType> transportTypes = parseEnums(request.transportTypes(), TransportType.class);
        List<FoodPreference> foods = request.foodPreferences().stream()
                .map(item -> new FoodPreference(item.food(), item.score()))
                .toList();
        String allergies = StringJsonArrays.toJson(normalizeAllergies(request.allergies()));
        String categories = EnumJsonArrays.toJson(parseEnums(request.travelCategories(), TravelCategory.class));
        Integer budgetMax = request.budget() == null ? null : request.budget().max();

        Preference preference = preferenceRepository.findByTripIdAndUserId(trip.getId(), userId)
                .orElse(null);
        if (preference == null) {
            User user = groupReader.getUser(userId);
            preference = preferenceRepository.save(Preference.builder()
                    .trip(trip)
                    .user(user)
                    .transportTypes(transportTypes)
                    .foodPreferences(foods)
                    .allergies(allergies)
                    .travelCategories(categories)
                    .budgetMax(budgetMax)
                    .build());
        } else {
            preference.replace(transportTypes, foods, allergies, categories, budgetMax);
        }

        if (request.excludedCategories().size() > 20 || request.excludedCategories().stream()
                .anyMatch(code -> code == null || !code.matches("[A-Z][A-Z0-9_]{0,49}"))) {
            throw new BusinessException(ErrorCode.INVALID_PREFERENCE);
        }
        preference.changeExcludedCategories(StringJsonArrays.toJson(new LinkedHashSet<>(request.excludedCategories())));
        preferenceRepository.flush(); // updated_at 반영
        return new SavePreferenceResponse(groupId, userId, true, preference.getUpdatedAt());
    }

    /**
     * 내 취향 조회 - 현재 여행 회차의 취향.
     * 없으면 같은 그룹의 가장 최근 회차 취향을 불러오고 loaded_from_previous=true, 그것도 없으면 404
     */
    public MyPreferenceResponse getMyPreference(Long userId, Long groupId) {
        groupReader.getMemberGroup(groupId, userId);
        Trip trip = groupReader.findCurrentTrip(groupId);
        if (trip == null) {
            throw new BusinessException(ErrorCode.PREFERENCE_NOT_FOUND);
        }

        return preferenceRepository.findByTripIdAndUserId(trip.getId(), userId)
                .map(preference -> MyPreferenceResponse.of(preference, false))
                .or(() -> preferenceRepository
                        .findFirstByTripGroupIdAndUserIdAndTripIdNotOrderByTripIdDesc(groupId, userId, trip.getId())
                        .map(previous -> MyPreferenceResponse.of(previous, true)))
                .orElseThrow(() -> new BusinessException(ErrorCode.PREFERENCE_NOT_FOUND));
    }

    /** 취향 통합 - 현재 여행 회차에서 현재 멤버가 입력한 취향만 집계 */
    public PreferenceSummaryResponse getSummary(Long userId, Long groupId) {
        Group group = groupReader.getMemberGroup(groupId, userId);
        Trip trip = groupReader.findCurrentTrip(groupId);
        List<Preference> preferences = trip == null ? List.of()
                : preferenceRepository.findAllOfCurrentMembers(trip.getId(), groupId);
        if (preferences.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_PREFERENCE_TO_SUMMARIZE);
        }
        return preferenceAggregator.aggregate(group, preferences);
    }

    /**
     * 알레르기 자유 입력 정리: 앞뒤 공백 제거, 빈 값·중복 제거(입력 순서 유지).
     * 정리한 뒤 20개를 넘거나 한 항목이 30자를 넘으면 400.
     */
    static List<String> normalizeAllergies(List<String> values) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String allergy = value.strip();
            if (allergy.length() > MAX_ALLERGY_LENGTH) {
                throw new BusinessException(ErrorCode.INVALID_PREFERENCE);
            }
            result.add(allergy);
        }
        if (result.size() > MAX_ALLERGIES) {
            throw new BusinessException(ErrorCode.INVALID_PREFERENCE);
        }
        return List.copyOf(result);
    }

    /** 문자열 목록을 enum 으로 변환. 모르는 값이 있으면 400, 중복은 하나로 합친다 */
    private static <E extends Enum<E>> Set<E> parseEnums(List<String> values, Class<E> type) {
        Set<E> result = new LinkedHashSet<>();
        for (String value : values) {
            E parsed = Arrays.stream(type.getEnumConstants())
                    .filter(constant -> constant.name().equals(value))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PREFERENCE));
            result.add(parsed);
        }
        return result;
    }
}
