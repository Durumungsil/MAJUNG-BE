package durumungsil.majung.domain.place.repository;

import durumungsil.majung.domain.place.entity.GooglePlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;

public interface GooglePlaceRepository extends JpaRepository<GooglePlace, String> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update GooglePlace p set p.latitude=null,p.longitude=null,p.locationCachedAt=null where p.locationCachedAt <= :cutoff")
    int expireCoordinates(Instant cutoff);
}
