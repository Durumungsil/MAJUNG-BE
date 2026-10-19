package durumungsil.majung.domain.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface StoreRepository extends JpaRepository<Store, Long> {
    @Query("select s from Store s where s.sido in :regions or s.sigungu in :regions order by s.destinationId")
    List<Store> findInRegions(List<String> regions);
}
