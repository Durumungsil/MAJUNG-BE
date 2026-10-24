package durumungsil.majung.domain.story.service;

import durumungsil.majung.domain.store.entity.Store;
import durumungsil.majung.domain.trip.entity.Trip;

import java.util.List;

/**
 * 그룹 조건(선호·예산·알레르기)에 맞는 장소만 남기는 필터.
 * 스토리모드 추천은 이 인터페이스만 의존한다.
 * 나침반 도메인의 "사용자 그룹 기반 필터링"이 완성되면 그 로직으로 구현체를 교체한다.
 * 그 전까지는 {@link PassThroughGroupPlaceFilter}가 후보를 그대로 돌려준다.
 */
public interface GroupPlaceFilter {

    /** 후보 순서는 유지하고 조건에 맞지 않는 장소만 뺀다 */
    List<Store> filter(Trip trip, List<Store> candidates);
}
