package durumungsil.majung.domain.story.controller;

import durumungsil.majung.domain.story.dto.request.CreateTimelineRequest;
import durumungsil.majung.domain.story.dto.request.UpdateTimelineItemRequest;
import durumungsil.majung.domain.story.dto.response.TimelineResponse;
import durumungsil.majung.domain.story.dto.response.UpdateTimelineItemResponse;
import durumungsil.majung.global.response.ApiResponse;
import durumungsil.majung.global.security.AuthUser;
import durumungsil.majung.domain.story.service.ItineraryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 스토리모드 - 여행 타임라인 */
@RestController
@RequestMapping("/api/v1/groups/{groupId}/itineraries")
@RequiredArgsConstructor
public class ItineraryController {

    private final ItineraryService itineraryService;

    /** AI 타임라인 생성 */
    @PostMapping("/timeline")
    public ResponseEntity<ApiResponse<TimelineResponse>> createTimeline(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long groupId,
            @Valid @RequestBody CreateTimelineRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("여행 타임라인이 생성되었습니다.",
                        itineraryService.createTimeline(authUser.id(), groupId, request)));
    }

    /** 타임라인 조회 */
    @GetMapping("/{itineraryId}")
    public ApiResponse<TimelineResponse> getTimeline(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long groupId,
            @PathVariable Long itineraryId) {
        return ApiResponse.success("여행 타임라인을 조회했습니다.",
                itineraryService.getTimeline(authUser.id(), groupId, itineraryId));
    }

    /** 타임라인 항목 수정 */
    @PatchMapping("/{itineraryId}/items/{itemId}")
    public ApiResponse<UpdateTimelineItemResponse> updateItem(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long groupId,
            @PathVariable Long itineraryId,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateTimelineItemRequest request) {
        return ApiResponse.success("타임라인 항목이 수정되었습니다.",
                itineraryService.updateItem(authUser.id(), groupId, itineraryId, itemId, request));
    }
}
