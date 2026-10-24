package durumungsil.majung.domain.story.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 타임라인 항목 수정 결과.
 * has_conflict: 바꾼 시간에 장소가 영업하지 않는 구간이 있으면 true (저장은 된다. 화면에서 경고용)
 */
public record UpdateTimelineItemResponse(
        Long itineraryId,
        Long itemId,
        @JsonFormat(pattern = "HH:mm") LocalTime startAt,
        @JsonFormat(pattern = "HH:mm") LocalTime endAt,
        String memo,
        boolean hasConflict,
        LocalDateTime updatedAt
) {
}
