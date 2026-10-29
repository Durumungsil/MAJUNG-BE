package durumungsil.majung.domain.story.dto.response;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import durumungsil.majung.domain.story.entity.ItineraryItemType;
import java.time.LocalTime;
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimelineItemResponse(Long itemId,int sequence,ItineraryItemType type,
        String placeId, @JsonInclude(JsonInclude.Include.ALWAYS) Double latitude,
        @JsonInclude(JsonInclude.Include.ALWAYS) Double longitude,
        @JsonFormat(pattern="HH:mm") LocalTime startAt,@JsonFormat(pattern="HH:mm") LocalTime endAt,Integer stayMinutes,
        String fromPlaceId,Double fromLatitude,Double fromLongitude,String toPlaceId,Double toLatitude,Double toLongitude,
        Integer durationMinutes,Integer distanceMeters,Boolean longDistance,
        @JsonInclude(JsonInclude.Include.ALWAYS) String memo) { }
