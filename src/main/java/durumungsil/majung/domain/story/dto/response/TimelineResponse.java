package durumungsil.majung.domain.story.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import durumungsil.majung.domain.preference.entity.TransportType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 타임라인 생성(201)·조회(200) 공통 응답.
 * generated_by: 생성 응답에만 있다. AI(Gemini) 또는 RULE(규칙 기반 - 키 없음·AI 실패·AI 동선이 더 나쁠 때)
 * max_move_meters: transport_type 으로 한 번에 이동하기 적정한 최대 거리(경로 m). 넘는 MOVE 는 long_distance=true
 */
public record TimelineResponse(
        Long itineraryId,
        Long groupId,
        LocalDate startDate,
        LocalDate endDate,
        TransportType transportType,
        int maxMoveMeters,
        @JsonFormat(pattern = "HH:mm") LocalTime dayStartTime,
        @JsonFormat(pattern = "HH:mm") LocalTime dayEndTime,
        List<Day> days,
        LocalDateTime generatedAt,
        LocalDateTime updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String generatedBy
) {
    public record Day(int day, LocalDate date, List<TimelineItemResponse> items) {
    }
}
