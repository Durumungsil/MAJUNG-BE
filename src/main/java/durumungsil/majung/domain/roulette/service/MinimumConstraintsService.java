package durumungsil.majung.domain.roulette.service;

import durumungsil.majung.domain.group.service.GroupReader;
import durumungsil.majung.domain.preference.entity.Preference;
import durumungsil.majung.domain.preference.repository.PreferenceRepository;
import durumungsil.majung.domain.roulette.dto.response.MinimumConstraintsResponse;
import durumungsil.majung.domain.trip.entity.Trip;
import durumungsil.majung.domain.trip.entity.TripMode;
import durumungsil.majung.global.exception.BusinessException;
import durumungsil.majung.global.exception.ErrorCode;
import durumungsil.majung.global.util.StringJsonArrays;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.TreeSet;
import durumungsil.majung.domain.preference.profile.ProfileReader;
import durumungsil.majung.domain.preference.profile.PreferenceProfileRepository;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MinimumConstraintsService {
    private final GroupReader groupReader;
    private final PreferenceRepository preferenceRepository;
    private final ProfileReader profiles;
    private final PreferenceProfileRepository profileRepository;

    public MinimumConstraintsResponse get(Long userId, Long groupId) {
        groupReader.getMemberGroup(groupId, userId);
        Trip trip = groupReader.findCurrentTrip(groupId);
        if (trip == null) {
            throw new BusinessException(ErrorCode.MINIMUM_CONSTRAINTS_NOT_FOUND);
        }
        validateMode(trip);
        return forTrip(trip);
    }

    /** 매번 현재 멤버의 현재 회차 취향을 읽어 탈퇴·취향 수정으로 낡은 캐시가 쓰이지 않게 한다. */
    public MinimumConstraintsResponse forTrip(Trip trip) {
        List<Preference> preferences = preferenceRepository.findAllOfCurrentMembers(
                trip.getId(), trip.getGroup().getId());
        if (preferences.isEmpty()) {
            throw new BusinessException(ErrorCode.MINIMUM_CONSTRAINTS_NOT_FOUND);
        }
        var profile=profiles.forTrip(trip);
        Instant updatedAt=profileRepository.findById(trip.getId()).map(p->p.getUpdatedAt()).orElseGet(()->preferences.stream()
                .map(Preference::getUpdatedAt).max(LocalDateTime::compareTo).orElseThrow().atZone(ZoneId.systemDefault()).toInstant());
        return new MinimumConstraintsResponse(trip.getGroup().getId(),profile.allergies().normalized().stream().map(Enum::name).toList(),
                profile.avoidPlaceTypes(),preferences.size(),updatedAt);

    }

    static void validateMode(Trip trip) {
        if (trip.getMode() != TripMode.EXTREME) {
            throw new BusinessException(ErrorCode.NOT_EXTREME_MODE);
        }
    }
}
