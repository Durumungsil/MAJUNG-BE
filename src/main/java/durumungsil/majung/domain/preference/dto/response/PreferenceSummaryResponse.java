package durumungsil.majung.domain.preference.dto.response;

import durumungsil.majung.domain.preference.entity.Allergy;
import durumungsil.majung.domain.preference.entity.TransportType;
import durumungsil.majung.domain.preference.entity.TravelCategory;

import java.util.List;

public record PreferenceSummaryResponse(
        Long groupId,
        List<TransportType> transportTypes,
        List<String> foodPreferences,
        List<TravelCategory> travelCategories,
        Budget budget,
        List<Allergy> allergies,
        int completedMemberCount,
        int memberCount
) {
    /** min = 멤버 예산 중 최솟값, max = 멤버 예산의 중앙값 */
    public record Budget(Integer min, Integer max, String aggregation) {

        public static final String MEDIAN = "MEDIAN";
    }
}
