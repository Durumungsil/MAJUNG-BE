package durumungsil.majung.global.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

class RegionAliasesTests {

    private static final List<String> BUSAN = List.of("BUSAN", "부산", "부산광역시");
    private static final List<String> DAEJEON = List.of("DAEJEON", "대전", "대전광역시");

    @Test
    void resolvesCityLevelRegions() {
        assertEquals(Optional.of(new RegionAliases.Scope(BUSAN, null)), RegionAliases.resolve("부산광역시"));
        assertEquals(Optional.of(new RegionAliases.Scope(DAEJEON, null)), RegionAliases.resolve("대전"));
        assertEquals(Optional.of(new RegionAliases.Scope(DAEJEON, null)), RegionAliases.resolve(" DAEJEON "));
    }

    @Test
    void resolvesDistrictsAndLandmarks() {
        var jin = Optional.of(new RegionAliases.Scope(BUSAN, "부산진구"));
        assertEquals(jin, RegionAliases.resolve("부산진구"));
        assertEquals(jin, RegionAliases.resolve("부산 부산진구"));
        assertEquals(jin, RegionAliases.resolve("서면"));
        assertEquals(jin, RegionAliases.resolve("부산광역시  서면"));
        assertEquals(Optional.of(new RegionAliases.Scope(DAEJEON, "유성구")), RegionAliases.resolve("유성구"));
        assertEquals(Optional.of(new RegionAliases.Scope(DAEJEON, "중구")), RegionAliases.resolve("대전 중구"));
        assertEquals(Optional.of(new RegionAliases.Scope(BUSAN, "중구")), RegionAliases.resolve("부산 중구"));
    }

    @Test
    void rejectsAmbiguousOrUnknownRegions() {
        assertTrue(RegionAliases.resolve("중구").isEmpty());
        assertTrue(RegionAliases.resolve("서구").isEmpty());
        assertTrue(RegionAliases.resolve("대전 부산진구").isEmpty());
        assertTrue(RegionAliases.resolve("대전 서면").isEmpty());
        assertTrue(RegionAliases.resolve("경주").isEmpty());
        assertTrue(RegionAliases.resolve(" ").isEmpty());
        assertTrue(RegionAliases.resolve(null).isEmpty());
    }

    @Test
    void keepsCityAliasLookupForRoulette() {
        assertEquals(BUSAN, RegionAliases.of("부산"));
        assertEquals(DAEJEON, RegionAliases.of("대전광역시"));
        assertEquals(List.of("경주"), RegionAliases.of("경주"));
    }
}
