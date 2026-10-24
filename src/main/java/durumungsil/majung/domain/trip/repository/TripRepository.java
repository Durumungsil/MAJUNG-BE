package durumungsil.majung.domain.trip.repository;

import durumungsil.majung.domain.trip.entity.Trip;
import durumungsil.majung.domain.trip.entity.TripStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

    /** 진행 중인 여행 회차 (PLANNING / IN_PROGRESS). 그룹당 하나만 존재해야 한다 */
    Optional<Trip> findFirstByGroupIdAndStatusInOrderByIdDesc(Long groupId, Collection<TripStatus> statuses);

    boolean existsByGroupIdAndStatusIn(Long groupId, Collection<TripStatus> statuses);

    /** 그룹의 여행 회차 최신순 */
    List<Trip> findAllByGroupIdOrderByIdDesc(Long groupId);

    /** 모험모드 - 끝나지 않은 여행 회차를 잠그고 가져온다 (최신순) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t where t.group.id = :groupId and t.status <> durumungsil.majung.domain.trip.entity.TripStatus.COMPLETED order by t.id desc")
    List<Trip> findActiveForUpdate(@Param("groupId") Long groupId);

    /** 여러 그룹의 여행 회차를 한 번에 (그룹 목록 조회용) */
    List<Trip> findAllByGroupIdInOrderByIdDesc(Collection<Long> groupIds);
}
