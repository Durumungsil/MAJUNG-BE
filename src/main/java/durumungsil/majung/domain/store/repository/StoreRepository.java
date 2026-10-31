package durumungsil.majung.domain.store.repository;

import durumungsil.majung.domain.store.entity.Store;
import durumungsil.majung.domain.store.entity.Type;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface StoreRepository extends JpaRepository<Store, Long> {

    /** 모험 미션의 지역 후보 수 상한. */
    int MAX_REGION_CANDIDATES = 500;

    /** 지역 후보 조회에 넘기는 개수 제한 */
    static Pageable regionCandidateLimit() {
        return PageRequest.of(0, MAX_REGION_CANDIDATES);
    }

    /** 모험 미션용 - 지역과 업종이 맞는 장소를 평점 높은 순(평점 없으면 뒤로)으로. 개수는 {@link #regionCandidateLimit()} 로 자른다. */
    @Query(
            """
            select s from Store s
            where (s.sido in :regions or s.sigungu in :regions)
              and s.destinationType in :types
            order by s.rating desc nulls last, s.destinationId
            """)
    List<Store> findInRegionsAndTypes(
            @Param("regions") Collection<String> regions,
            @Param("types") Collection<Type> types,
            Pageable limit);
}
