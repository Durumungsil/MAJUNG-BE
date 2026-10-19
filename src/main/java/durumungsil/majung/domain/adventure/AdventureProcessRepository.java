package durumungsil.majung.domain.adventure;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface AdventureProcessRepository extends JpaRepository<AdventureProcess, Long> {
    boolean existsByTripIdAndTravelDate(Long tripId, LocalDate travelDate);
    Optional<AdventureProcess> findByTripIdAndTravelDate(Long tripId, LocalDate travelDate);
}
