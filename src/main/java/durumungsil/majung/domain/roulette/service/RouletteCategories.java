package durumungsil.majung.domain.roulette.service;

import durumungsil.majung.domain.store.entity.Store;
import durumungsil.majung.domain.store.entity.Type;

public final class RouletteCategories {
    private RouletteCategories() {
    }

    /** 세부 분류를 우선하고, 없는 경우 기존 업종에서 확실한 대분류만 사용한다. */
    public static String of(Store store) {
        if (store.getPlaceCategory() != null) return store.getPlaceCategory();
        if (store.getDestinationType() == Type.FOOD) return "FOOD";
        if (store.getDestinationType() == Type.RETAIL) return "SHOPPING";
        if (store.getDestinationType() == Type.ART_SPORTS) return "CULTURE";
        return null;
    }
}
