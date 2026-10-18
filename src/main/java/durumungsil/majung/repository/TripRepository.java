package durumungsil.majung.repository;

import durumungsil.majung.domain.trip.Trip;
import durumungsil.majung.domain.trip.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

    /** 진행 중인 여행 회차 (PLANNING / IN_PROGRESS). 그룹당 하나만 존재해야 한다 */
    Optional<Trip> findFirstByGroupIdAndStatusInOrderByIdDesc(Long groupId, Collection<TripStatus> statuses);

    boolean existsByGroupIdAndStatusIn(Long groupId, Collection<TripStatus> statuses);

    /** 그룹의 여행 회차 최신순 */
    List<Trip> findAllByGroupIdOrderByIdDesc(Long groupId);

    /** 여러 그룹의 여행 회차를 한 번에 (그룹 목록 조회용) */
    List<Trip> findAllByGroupIdInOrderByIdDesc(Collection<Long> groupIds);
}
