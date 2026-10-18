package durumungsil.majung.repository;

import durumungsil.majung.domain.preference.Preference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PreferenceRepository extends JpaRepository<Preference, Long> {

    Optional<Preference> findByTripIdAndUserId(Long tripId, Long userId);

    /** 해당 여행 회차에서 취향을 입력한 사용자 id 들 */
    @Query("select p.user.id from Preference p where p.trip.id = :tripId")
    List<Long> findUserIdsByTripId(@Param("tripId") Long tripId);

    /** 사용자가 취향을 입력한 여행 회차 id 들 (그룹 목록 조회용) */
    @Query("select p.trip.id from Preference p where p.user.id = :userId and p.trip.id in :tripIds")
    List<Long> findTripIdsByUserIdAndTripIdIn(@Param("userId") Long userId,
                                              @Param("tripIds") Collection<Long> tripIds);

    /** 현재 그룹 멤버가 입력한 취향만 (탈퇴한 멤버의 취향은 남아 있지만 집계에서 제외) */
    @Query("""
            select p from Preference p
            where p.trip.id = :tripId
              and p.user.id in (select gm.user.id from GroupMember gm where gm.group.id = :groupId)
            """)
    List<Preference> findAllOfCurrentMembers(@Param("tripId") Long tripId, @Param("groupId") Long groupId);

    @Query("""
            select count(p) from Preference p
            where p.trip.id = :tripId
              and p.user.id in (select gm.user.id from GroupMember gm where gm.group.id = :groupId)
            """)
    long countOfCurrentMembers(@Param("tripId") Long tripId, @Param("groupId") Long groupId);

    /** 같은 그룹의 이전 여행 회차 중 가장 최근에 입력한 내 취향 (내 취향 불러오기용) */
    Optional<Preference> findFirstByTripGroupIdAndUserIdAndTripIdNotOrderByTripIdDesc(
            Long groupId, Long userId, Long excludedTripId);
}
