package durumungsil.majung.domain.adventure.service;

import durumungsil.majung.domain.preference.profile.Allergy;
import durumungsil.majung.domain.store.entity.Store;
import durumungsil.majung.domain.store.entity.Type;

import java.util.*;

/** 업종 소분류로 명백히 충돌하는 후보를 제외한다. 원재료·교차 접촉을 판정하는 기능은 아니다. */
final class StoreAllergyFilter {
    private StoreAllergyFilter() {}

    private static final Map<Allergy, List<String>> RISK =
            Map.ofEntries(
                    Map.entry(
                            Allergy.SHELLFISH,
                            List.of(
                                    "해산물", "해물", "생선", "횟집", "일식", "초밥", "중식", "중국", "마라", "조개",
                                    "새우", "게요리")),
                    Map.entry(
                            Allergy.FISH,
                            List.of("해산물", "해물", "생선", "횟집", "일식", "초밥", "수산", "장어", "복요리")),
                    Map.entry(
                            Allergy.PEANUT,
                            List.of("중식", "중국", "마라", "동남아", "태국", "베트남", "제과", "빵", "떡", "디저트")),
                    Map.entry(Allergy.TREE_NUT, List.of("제과", "빵", "떡", "디저트", "아이스크림")),
                    Map.entry(
                            Allergy.MILK,
                            List.of(
                                    "제과", "빵", "디저트", "아이스크림", "피자", "파스타", "서양", "양식", "커피",
                                    "카페")),
                    Map.entry(Allergy.EGG, List.of("제과", "빵", "디저트", "분식", "김밥", "샌드위치")),
                    Map.entry(
                            Allergy.WHEAT,
                            List.of(
                                    "제과", "빵", "국수", "면", "분식", "피자", "파스타", "햄버거", "샌드위치", "치킨",
                                    "중식", "중국")),
                    Map.entry(Allergy.SOY, List.of("두부", "콩", "한식", "중식", "중국", "일식")),
                    Map.entry(Allergy.SESAME, List.of("한식", "분식", "김밥", "중식", "중국", "일식")),
                    Map.entry(Allergy.BUCKWHEAT, List.of("메밀", "냉면", "막국수", "국수")));

    static boolean allows(Store store, Collection<Allergy> allergies) {
        if (store.getDestinationType() != Type.FOOD || allergies.isEmpty()) return true;
        String name = store.getIndustrySmallName();
        if (name == null || name.isBlank() || allergies.contains(Allergy.UNKNOWN)) return false;
        String normalized = name.replace(" ", "");
        return allergies.stream()
                .flatMap(a -> RISK.getOrDefault(a, List.of()).stream())
                .noneMatch(normalized::contains);
    }
}
