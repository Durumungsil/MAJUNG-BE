package durumungsil.majung.domain.preference.profile;

import durumungsil.majung.domain.group.service.GroupReader;
import durumungsil.majung.domain.preference.repository.PreferenceRepository;
import durumungsil.majung.domain.trip.entity.Trip;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class ProfileLifecycle {
    private final PreferenceProfileRepository profiles;
    private final PreferenceRepository preferences;
    private final GroupReader groupReader;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** 호출자는 먼저 그룹 행을 잠근다. 불완전한 설문도 이전 작업을 무효화한다. */
    @Transactional
    public void invalidate(Trip trip) {
        PreferenceProfile profile = profiles.findById(trip.getId()).orElse(null);
        if (profile == null) profile = profiles.save(new PreferenceProfile(trip, clock.instant()));
        else profile.invalidate(clock.instant());
        if (trip.getGroup().getMemberCount() > 0
                && preferences.countOfCurrentMembers(trip.getId(), trip.getGroup().getId())
                        == trip.getGroup().getMemberCount())
            events.publishEvent(new ProfileJob(trip.getId(), profile.getVersion()));
    }

    @Transactional
    public void membershipChanged(Long groupId) {
        groupReader.findActiveTrip(groupId).ifPresent(this::invalidate);
    }
}
