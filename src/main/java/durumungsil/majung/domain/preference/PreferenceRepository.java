package durumungsil.majung.domain.preference;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface PreferenceRepository extends JpaRepository<Preference, Long> {
    @Query("select p from Preference p where p.trip.id = :tripId and p.user.id in "
            + "(select m.user.id from GroupMember m where m.group.id = :groupId)")
    List<Preference> findCurrentMemberPreferences(Long tripId, Long groupId);
}
