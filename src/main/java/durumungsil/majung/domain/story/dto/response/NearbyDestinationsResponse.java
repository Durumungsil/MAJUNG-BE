package durumungsil.majung.domain.story.dto.response;

import durumungsil.majung.domain.preference.entity.TransportType;
import durumungsil.majung.domain.preference.entity.TravelCategory;

import java.util.List;

/**
 * 2차 추천 - 기준 여행지 주변 여행지.
 * radius_meters 는 실제로 쓴 반경(요청 값 또는 교통수단별 기본값), transport_type 은 이동 시간 어림에 쓴 교통수단.
 */
public record NearbyDestinationsResponse(
        BaseDestination baseDestination,
        int radiusMeters,
        TransportType transportType,
        List<Item> items
) {
    public record BaseDestination(Long destinationId, String name) {
    }

    public record Item(
            Long destinationId,
            String name,
            TravelCategory category,
            int distanceMeters,
            double score,
            int estimatedTravelMinutes
    ) {
    }
}
