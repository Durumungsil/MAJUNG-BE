package durumungsil.majung.domain.story.repository;

import durumungsil.majung.domain.story.entity.ItineraryItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItineraryItemRepository extends JpaRepository<ItineraryItem, Long> {

    /** 타임라인 전체 항목 (day, sequence 순). VISIT 의 장소를 함께 가져온다 */
    @Query(
            """
            select i from ItineraryItem i
            left join fetch i.googlePlace
            where i.itinerary.id = :itineraryId
            order by i.day asc, i.sequence asc
            """)
    List<ItineraryItem> findAllByItineraryIdWithPlace(@Param("itineraryId") Long itineraryId);

    @Modifying(flushAutomatically = true)
    @Query("delete from ItineraryItem i where i.itinerary.id in :itineraryIds")
    int deleteAllByItineraryIdIn(@Param("itineraryIds") List<Long> itineraryIds);
}
