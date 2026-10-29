package durumungsil.majung.domain.preference.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PreferenceProfileRepository extends JpaRepository<PreferenceProfile,Long> {
    @Query("select p.trip.group.id from PreferenceProfile p where p.tripId=:tripId")
    Optional<Long> findGroupId(Long tripId);
    @Query("""
            select p.tripId from PreferenceProfile p
            where p.status in (durumungsil.majung.domain.preference.profile.ProfileStatus.PENDING,
                               durumungsil.majung.domain.preference.profile.ProfileStatus.RUNNING)
              and p.updatedAt < :cutoff and p.trip.group.memberCount > 0
              and (select count(opinion) from Preference opinion where opinion.trip = p.trip
                   and exists (select member.id from GroupMember member
                               where member.group = p.trip.group and member.user = opinion.user)) = p.trip.group.memberCount
            order by p.updatedAt
            """)
    List<Long> findStale(Instant cutoff, Pageable pageable);
}
