package durumungsil.majung.domain.preference.profile;

import durumungsil.majung.domain.group.repository.GroupRepository;
import durumungsil.majung.domain.preference.repository.PreferenceRepository;
import durumungsil.majung.domain.preference.service.PreferenceAggregator;
import durumungsil.majung.global.util.StringJsonArrays;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProfileTransactions {
    private final PreferenceProfileRepository profiles;
    private final GroupRepository groups;
    private final PreferenceRepository preferences;
    private final PreferenceAggregator aggregator;
    private final ObjectMapper mapper;
    private final Clock clock;

    public record Work(
            Long tripId,
            long version,
            String token,
            String region,
            PreferenceProfileData rules,
            List<String> rawAllergies) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Work> claim(ProfileJob job) {
        var profile = locked(job.tripId());
        if (profile == null || profile.getVersion() != job.version()) return Optional.empty();
        if (profile.getStatus() == ProfileStatus.DONE
                || profile.getStatus() == ProfileStatus.FAILED) return Optional.empty();
        if (profile.getStatus() == ProfileStatus.RUNNING
                && profile.getUpdatedAt().isAfter(clock.instant().minus(Duration.ofMinutes(5))))
            return Optional.empty();
        var trip = profile.getTrip();
        var opinions = preferences.findAllOfCurrentMembers(trip.getId(), trip.getGroup().getId());
        if (opinions.isEmpty() || opinions.size() != trip.getGroup().getMemberCount())
            return Optional.empty();
        String token = UUID.randomUUID().toString();
        profile.start(token, clock.instant());
        var raw =
                opinions.stream()
                        .flatMap(p -> StringJsonArrays.fromJson(p.getAllergies()).stream())
                        .distinct()
                        .toList();
        return Optional.of(
                new Work(
                        trip.getId(),
                        profile.getVersion(),
                        token,
                        trip.getRegion(),
                        aggregator.profile(trip, opinions, profile.getVersion()),
                        raw));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean finish(Work work, PreferenceProfileData data) {
        var profile = locked(work.tripId());
        if (!matches(profile, work)) return false;
        profile.finish(mapper.writeValueAsString(data), data.generatedBy(), clock.instant());
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Work work) {
        var profile = locked(work.tripId());
        if (matches(profile, work)) profile.fail(clock.instant());
    }

    private boolean matches(PreferenceProfile profile, Work work) {
        return profile != null
                && profile.getVersion() == work.version()
                && profile.getStatus() == ProfileStatus.RUNNING
                && Objects.equals(profile.getRunToken(), work.token());
    }

    private PreferenceProfile locked(Long tripId) {
        var groupId = profiles.findGroupId(tripId);
        if (groupId.isEmpty() || groups.findByIdForUpdate(groupId.get()).isEmpty()) return null;
        return profiles.findById(tripId).orElse(null);
    }
}
