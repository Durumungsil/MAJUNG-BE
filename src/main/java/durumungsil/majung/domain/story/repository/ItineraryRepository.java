package durumungsil.majung.domain.story.repository;

import durumungsil.majung.domain.story.entity.Itinerary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItineraryRepository extends JpaRepository<Itinerary, Long> {

    @Query("select i.id from Itinerary i where i.trip.id = :tripId")
    List<Long> findIdsByTripId(@Param("tripId") Long tripId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Itinerary i where i.id in :ids")
    int deleteAllByIdIn(@Param("ids") List<Long> ids);
}
