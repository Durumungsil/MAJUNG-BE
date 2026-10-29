package durumungsil.majung.domain.story.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * AI 타임라인 생성.
 * 모든 값은 선택이다. 비어 있으면 아래 기본값을 쓴다.
 * - start_date / end_date: 여행 회차의 여행 기간
 * - place_ids: 여행지 선택 저장으로 저장한 여행지 (각자 저장한 방문일에 배치)
 * - transport_type: 그룹 취향에서 가장 많이 고른 교통수단, 그것도 없으면 BUS
 * - day_start_time / day_end_time: 09:00 / 21:00
 */
public record CreateTimelineRequest(
        LocalDate startDate,
        LocalDate endDate,
        @Size(max=50) List<@NotNull(message = MESSAGE) @Pattern(regexp="[A-Za-z0-9_-]{1,255}",message=MESSAGE) String> placeIds,
        String transportType,
        LocalTime dayStartTime,
        LocalTime dayEndTime
) {
    static final String MESSAGE = "여행 기간, 교통수단 또는 하루 일정 시간이 올바르지 않습니다.";
}
