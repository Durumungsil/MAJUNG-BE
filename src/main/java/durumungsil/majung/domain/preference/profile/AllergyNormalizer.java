package durumungsil.majung.domain.preference.profile;

import java.util.*;

/** AI가 없어도 작동하는 보수적인 정규화. 판별하지 못한 원문은 UNKNOWN으로 표시한다. */
public final class AllergyNormalizer {
    private static final Map<Allergy, List<String>> WORDS = Map.ofEntries(
            Map.entry(Allergy.SHELLFISH, List.of("갑각", "조개", "패류", "새우", "꽃게", "대게", "가재", "굴", "홍합", "전복", "shellfish", "shrimp", "crab", "lobster")),
            Map.entry(Allergy.FISH, List.of("생선", "어류", "고등어", "fish")),
            Map.entry(Allergy.PEANUT, List.of("땅콩", "peanut")),
            Map.entry(Allergy.TREE_NUT, List.of("견과", "호두", "아몬드", "캐슈", "tree_nut", "walnut", "almond")),
            Map.entry(Allergy.MILK, List.of("우유", "유제품", "유당", "milk", "dairy")),
            Map.entry(Allergy.EGG, List.of("계란", "달걀", "난류", "egg")),
            Map.entry(Allergy.WHEAT, List.of("밀", "글루텐", "wheat", "gluten")),
            Map.entry(Allergy.SOY, List.of("대두", "soy")),
            Map.entry(Allergy.SESAME, List.of("참깨", "sesame")),
            Map.entry(Allergy.BUCKWHEAT, List.of("메밀", "buckwheat")));
    private AllergyNormalizer() { }
    public static List<Allergy> normalize(Collection<String> raw) {
        Set<Allergy> result = EnumSet.noneOf(Allergy.class);
        for (String value : raw) {
            if (value == null || value.isBlank()) continue;
            try {
                result.add(Allergy.valueOf(value.strip().toUpperCase(Locale.ROOT)));
                continue;
            } catch (IllegalArgumentException ignored) { }
            String text = value.toLowerCase(Locale.ROOT).replace(" ", "");
            Set<Allergy> found = EnumSet.noneOf(Allergy.class);
            WORDS.forEach((allergy, words) -> { if (words.stream().anyMatch(word -> text.contains(word)
                    && !(word.equals("fish") && text.contains("shellfish")))) found.add(allergy); });
            if(text.equals("콩") || text.equals("콩알레르기")) found.add(Allergy.SOY);
            if (found.isEmpty()) found.add(Allergy.UNKNOWN);
            result.addAll(found);
        }
        return List.copyOf(result);
    }
    public static Set<String> avoidTypes(Collection<Allergy> allergies) {
        Set<String> avoid = new TreeSet<>();
        if (allergies.contains(Allergy.SHELLFISH) || allergies.contains(Allergy.FISH))
            avoid.addAll(List.of("seafood_restaurant", "sushi_restaurant"));
        if (allergies.contains(Allergy.WHEAT) || allergies.contains(Allergy.EGG) || allergies.contains(Allergy.MILK))
            avoid.addAll(List.of("bakery", "dessert_shop"));
        if (allergies.contains(Allergy.MILK)) avoid.add("ice_cream_shop");
        return avoid;
    }
}
