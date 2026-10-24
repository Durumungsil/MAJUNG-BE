package durumungsil.majung.domain.adventure.repository;

import durumungsil.majung.domain.adventure.entity.Mission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MissionRepository extends JpaRepository<Mission, Long> {
    List<Mission> findByProcessIdOrderByMissionOrderAsc(Long processId);
}
