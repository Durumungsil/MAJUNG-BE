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

    // 두 추첨 쿼리가 같은 필수 조건을 사용한다. 평점 상위 후보 제한을 쓰지 않아 전체 적격 장소에 같은 확률을 준다.
    String ROULETTE_CATEGORY = """
            coalesce(s.placeCategory, case
                when s.destinationType = durumungsil.majung.domain.store.entity.Type.FOOD then 'FOOD'
                when s.destinationType = durumungsil.majung.domain.store.entity.Type.RETAIL then 'SHOPPING'
                when s.destinationType = durumungsil.majung.domain.store.entity.Type.ART_SPORTS then 'CULTURE'
                else 'UNKNOWN' end)
            """;
    String ROULETTE_WHERE = """
            from Store s
            where (s.sido in :regions or s.sigungu in :regions)
              and s.destinationType in :types
              and s.latitude between -90 and 90 and s.longitude between -180 and 180
              and s.destinationName is not null and trim(s.destinationName) <> ''
              and s.destinationId not in :excludedIds
              and (:category = '' or
            """ + ROULETTE_CATEGORY + " = :category) and "
            + ROULETTE_CATEGORY + """
             not in :excludedCategories
              and not exists (select 1 from Store tagged join tagged.safetyCategories code
                              where tagged = s and code in :excludedCategories)
            """;

    @Query("select count(s) " + ROULETTE_WHERE)
    long countRouletteCandidates(@Param("regions") Collection<String> regions,
                                 @Param("types") Collection<Type> types,
                                 @Param("category") String category,
                                 @Param("excludedIds") Collection<Long> excludedIds,
                                 @Param("excludedCategories") Collection<String> excludedCategories);

    @Query("select s " + ROULETTE_WHERE + " order by s.destinationId")
    List<Store> findRouletteCandidates(@Param("regions") Collection<String> regions,
                                       @Param("types") Collection<Type> types,
                                       @Param("category") String category,
                                       @Param("excludedIds") Collection<Long> excludedIds,
                                       @Param("excludedCategories") Collection<String> excludedCategories,
                                       Pageable limit);


    /**
     * 지역·반경 후보로 한 번에 불러올 최대 장소 수.
     * 지역 전체(수만 건)를 불러오면 응답이 1분 넘게 걸리고 DB 연결이 끊기기도 해서,
     * 그룹 기반 필터링이 생기기 전까지는 평점 상위 일부만 후보로 쓴다.
     */
    int MAX_REGION_CANDIDATES = 500;

    /** 지역 후보 조회에 넘기는 개수 제한 */
    static Pageable regionCandidateLimit() {
        return PageRequest.of(0, MAX_REGION_CANDIDATES);
    }

    /**
     * 여행지 리스트 추천·모험 미션용 - 지역과 업종이 맞는 장소를 평점 높은 순(평점 없으면 뒤로)으로.
     * 개수는 {@link #regionCandidateLimit()} 로 자른다.
     */
    @Query("""
            select s from Store s
            where (s.sido in :regions or s.sigungu in :regions)
              and s.destinationType in :types
            order by s.rating desc nulls last, s.destinationId
            """)
    List<Store> findInRegionsAndTypes(@Param("regions") Collection<String> regions,
                                      @Param("types") Collection<Type> types,
                                      Pageable limit);

    /**
     * 여행지 리스트 추천용 - 지역·업종이 맞고 위도·경도 사각 범위 안에 있는 장소를 중심에서 가까운 순으로.
     * 정확한 반경은 서비스에서 거른다. 개수는 {@link #regionCandidateLimit()} 로 자른다.
     *
     * @param longitudeScale 경도 차이를 위도 차이와 같은 거리로 맞추는 계수 cos²(위도)
     */
    @Query("""
            select s from Store s
            where (s.sido in :regions or s.sigungu in :regions)
              and s.destinationType in :types
              and s.latitude between :minLatitude and :maxLatitude
              and s.longitude between :minLongitude and :maxLongitude
            order by (s.latitude - :centerLatitude) * (s.latitude - :centerLatitude)
                     + (s.longitude - :centerLongitude) * (s.longitude - :centerLongitude) * :longitudeScale,
                     s.destinationId
            """)
    List<Store> findInRegionsNearAndTypes(@Param("regions") Collection<String> regions,
                                          @Param("types") Collection<Type> types,
                                          @Param("minLatitude") double minLatitude,
                                          @Param("maxLatitude") double maxLatitude,
                                          @Param("minLongitude") double minLongitude,
                                          @Param("maxLongitude") double maxLongitude,
                                          @Param("centerLatitude") double centerLatitude,
                                          @Param("centerLongitude") double centerLongitude,
                                          @Param("longitudeScale") double longitudeScale,
                                          Pageable limit);

    /**
     * 2차 추천용 - 위도·경도 사각 범위 안에서 업종이 맞는 장소를 중심에서 가까운 순으로.
     * 정확한 반경은 서비스에서 거른다. 개수는 {@link #regionCandidateLimit()} 로 자른다.
     */
    @Query("""
            select s from Store s
            where s.latitude between :minLatitude and :maxLatitude
              and s.longitude between :minLongitude and :maxLongitude
              and s.destinationType in :types
            order by (s.latitude - :centerLatitude) * (s.latitude - :centerLatitude)
                     + (s.longitude - :centerLongitude) * (s.longitude - :centerLongitude) * :longitudeScale,
                     s.destinationId
            """)
    List<Store> findNearAndTypes(@Param("minLatitude") double minLatitude,
                                 @Param("maxLatitude") double maxLatitude,
                                 @Param("minLongitude") double minLongitude,
                                 @Param("maxLongitude") double maxLongitude,
                                 @Param("centerLatitude") double centerLatitude,
                                 @Param("centerLongitude") double centerLongitude,
                                 @Param("longitudeScale") double longitudeScale,
                                 @Param("types") Collection<Type> types,
                                 Pageable limit);
}
