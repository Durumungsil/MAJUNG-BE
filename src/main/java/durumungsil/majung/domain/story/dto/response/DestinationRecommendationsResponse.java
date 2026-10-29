package durumungsil.majung.domain.story.dto.response;
import durumungsil.majung.domain.preference.entity.*;
import java.util.List;
public record DestinationRecommendationsResponse(String region,Area area,List<Item> items,String nextCursor) {
    public record Area(double latitude,double longitude,int radiusMeters,TransportType transportType,String basedOn) { }
    /** name은 이번 Google 응답에서만 전달하며 서버에 저장하지 않는다. */
    public record Item(String placeId,String name,TravelCategory category,Double latitude,Double longitude,
                       int distanceMeters,double score,String reason) { }
}
