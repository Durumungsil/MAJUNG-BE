package durumungsil.majung.domain.story.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import durumungsil.majung.domain.story.entity.ItineraryItemType;

import java.time.LocalTime;

/**
 * 타임라인 항목 (생성·조회 공통).
 * - VISIT: destination_id, destination_name, start_at, end_at, stay_minutes
 * - MOVE : from_*(바로 앞 VISIT), to_*(바로 뒤 VISIT), duration_minutes, distance_meters,
 *          long_distance(이동 거리가 교통수단의 적정 거리 max_move_meters 를 넘는지)
 * 해당하지 않는 값은 내려주지 않는다. memo 는 항상 내려준다(없으면 null).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimelineItemResponse(
        Long itemId,
        int sequence,
        ItineraryItemType type,
        Long destinationId,
        String destinationName,
        @JsonFormat(pattern = "HH:mm") LocalTime startAt,
        @JsonFormat(pattern = "HH:mm") LocalTime endAt,
        Integer stayMinutes,
        Long fromDestinationId,
        String fromDestinationName,
        Long toDestinationId,
        String toDestinationName,
        Integer durationMinutes,
        Integer distanceMeters,
        Boolean longDistance,
        @JsonInclude(JsonInclude.Include.ALWAYS) String memo
) {
}
