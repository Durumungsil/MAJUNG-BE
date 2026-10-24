package durumungsil.majung.domain.story.dto.response;

import durumungsil.majung.domain.preference.entity.TransportType;
import durumungsil.majung.domain.preference.entity.TravelCategory;

import java.util.List;

/**
 * 여행지 리스트 추천.
 * area 는 추천 범위(중심과 반경)이고, 후보가 없으면 null 이다.
 * next_cursor 는 다음 페이지 요청의 cursor 로 그대로 넘기면 되고, 마지막 페이지면 null 이다.
 */
public record DestinationRecommendationsResponse(
        String region,
        Area area,
        List<Item> items,
        String nextCursor
) {
    /**
     * 추천 범위. 그룹 교통수단으로 다니기 적당한 반경 안의 장소만 추천한다.
     *
     * @param basedOn SELECTION(저장한 여행지 선택 중 나머지와의 거리 합이 가장 작은 장소) 또는 TOP_RECOMMENDATION(취향 점수 1위 장소)
     */
    public record Area(
            double latitude,
            double longitude,
            int radiusMeters,
            TransportType transportType,
            String basedOn
    ) {
    }

    /** @param distanceMeters 추천 범위 중심에서의 직선거리(m) */
    public record Item(
            Long destinationId,
            String name,
            TravelCategory category,
            String address,
            Double latitude,
            Double longitude,
            int distanceMeters,
            double score,
            String reason
    ) {
    }
}
