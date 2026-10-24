package durumungsil.majung.domain.story.service;

import durumungsil.majung.domain.store.entity.Store;
import durumungsil.majung.domain.trip.entity.Trip;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 임시 구현: 그룹 기반 필터링이 준비되기 전까지 아무 장소도 걸러내지 않는다.
 * TODO 나침반 "사용자 그룹 기반 필터링"(POST /api/v2/groups/{groupId}/places/filter) 구현 후 교체
 */
@Component
public class PassThroughGroupPlaceFilter implements GroupPlaceFilter {

    @Override
    public List<Store> filter(Trip trip, List<Store> candidates) {
        return candidates;
    }
}
