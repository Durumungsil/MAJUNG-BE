package durumungsil.majung.domain.story.dto.request;

import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/**
 * 타임라인 항목 수정 (VISIT 의 시간·메모).
 * 보내지 않은 값은 그대로 둔다. memo 를 빈 문자열로 보내면 메모를 지운다.
 */
public record UpdateTimelineItemRequest(
        LocalTime startAt,
        LocalTime endAt,
        @Size(max = 255, message = "메모는 255자 이하로 입력해 주세요.") String memo
) {
}
