package durumungsil.majung.domain.preference.profile;

import durumungsil.majung.domain.preference.repository.PreferenceRepository;
import durumungsil.majung.domain.preference.service.PreferenceAggregator;
import durumungsil.majung.domain.trip.entity.Trip;
import durumungsil.majung.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service @RequiredArgsConstructor
public class ProfileReader {
    private final PreferenceProfileRepository profiles;
    private final PreferenceRepository preferences;
    private final PreferenceAggregator aggregator;
    private final ObjectMapper mapper;
    @Transactional(readOnly=true)
    public PreferenceProfileData forTrip(Trip trip) {
        var opinions=preferences.findAllOfCurrentMembers(trip.getId(),trip.getGroup().getId());
        if(opinions.isEmpty() || opinions.size()!=trip.getGroup().getMemberCount())
            throw new BusinessException(ErrorCode.PREFERENCE_NOT_MERGED);
        var profile=profiles.findById(trip.getId()).orElse(null);
        if(profile!=null && profile.getStatus()==ProfileStatus.DONE && profile.getProfile()!=null) {
            try {
                var data=mapper.readValue(profile.getProfile(),PreferenceProfileData.class);
                if(data.version()==profile.getVersion()) return data;
            } catch(RuntimeException ignored) { /* 캐시가 손상되면 현재 설문 수치로 복구한다. */ }
        }
        return aggregator.profile(trip,opinions,profile==null?0:profile.getVersion());
    }
}
