package durumungsil.majung.domain.preference.dto.response;

import durumungsil.majung.domain.preference.entity.TransportType;
import durumungsil.majung.domain.preference.entity.TravelCategory;
import durumungsil.majung.domain.preference.profile.ProfileStatus;

import java.util.List;

public record PreferenceSummaryResponse(
        Long groupId,
        List<TransportType> transportTypes,
        List<String> foodPreferences,
        List<TravelCategory> travelCategories,
        Budget budget,
        List<String> allergies,
        int completedMemberCount,
        int memberCount,
        ProfileStatus profileStatus) {
    public PreferenceSummaryResponse(
            Long groupId,
            List<TransportType> transports,
            List<String> foods,
            List<TravelCategory> categories,
            Budget budget,
            List<String> allergies,
            int completed,
            int members) {
        this(
                groupId,
                transports,
                foods,
                categories,
                budget,
                allergies,
                completed,
                members,
                ProfileStatus.PENDING);
    }

    public PreferenceSummaryResponse withProfileStatus(ProfileStatus status) {
        return new PreferenceSummaryResponse(
                groupId,
                transportTypes,
                foodPreferences,
                travelCategories,
                budget,
                allergies,
                completedMemberCount,
                memberCount,
                status);
    }

    /** min = 멤버 예산 중 최솟값, max = 멤버 예산의 중앙값 */
    public record Budget(Integer min, Integer max, String aggregation) {

        public static final String MEDIAN = "MEDIAN";
    }
}
