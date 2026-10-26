package durumungsil.majung.domain.roulette.repository;

import durumungsil.majung.domain.roulette.entity.RouletteResult;
import durumungsil.majung.domain.roulette.entity.RouletteType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RouletteResultRepository extends JpaRepository<RouletteResult, Long> {
    List<RouletteResult> findAllByTripIdAndTypeOrderByRoundAsc(Long tripId, RouletteType type);

    Optional<RouletteResult> findByIdAndTripGroupId(Long id, Long groupId);
}
