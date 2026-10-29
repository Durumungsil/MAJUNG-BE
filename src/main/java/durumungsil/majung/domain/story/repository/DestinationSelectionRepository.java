package durumungsil.majung.domain.story.repository;

import durumungsil.majung.domain.story.entity.DestinationSelection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DestinationSelectionRepository extends JpaRepository<DestinationSelection, Long> {

    /** 여행 회차의 선택 여행지 (방문일, 방문 순서 순) */
    @Query("""
            select d from DestinationSelection d
            join fetch d.googlePlace
            where d.trip.id = :tripId
            order by d.visitDate asc, d.visitOrder asc
            """)
    List<DestinationSelection> findAllByTripIdWithPlace(@Param("tripId") Long tripId);

    /** 선택 저장(PUT) 시 기존 선택을 통째로 지운다 */
    @Modifying(flushAutomatically = true)
    @Query("delete from DestinationSelection d where d.trip.id = :tripId")
    int deleteAllByTripId(@Param("tripId") Long tripId);
}
