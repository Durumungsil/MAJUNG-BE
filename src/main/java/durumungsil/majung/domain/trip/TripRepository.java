package durumungsil.majung.domain.trip;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.List;

public interface TripRepository extends JpaRepository<Trip, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t where t.group.id = :groupId and t.status <> durumungsil.majung.domain.trip.TripStatus.COMPLETED order by t.id desc")
    List<Trip> findActiveForUpdate(Long groupId);
}
