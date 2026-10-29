package durumungsil.majung.domain.story.controller;

import durumungsil.majung.domain.story.dto.request.SaveDestinationSelectionRequest;
import durumungsil.majung.domain.story.dto.response.DestinationRecommendationsResponse;
import durumungsil.majung.domain.story.dto.response.NearbyDestinationsResponse;
import durumungsil.majung.domain.story.dto.response.SaveDestinationSelectionResponse;
import durumungsil.majung.global.response.ApiResponse;
import durumungsil.majung.global.security.AuthUser;
import durumungsil.majung.domain.story.service.DestinationRecommendationService;
import durumungsil.majung.domain.story.service.DestinationSelectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 스토리모드 - 여행지 추천·선택 */
@RestController
@RequestMapping("/api/v1/groups/{groupId}/destinations")
@RequiredArgsConstructor
public class DestinationController {

    private final DestinationRecommendationService recommendationService;
    private final DestinationSelectionService selectionService;

    /** 여행지 리스트 추천 */
    @GetMapping("/recommendations")
    public ApiResponse<DestinationRecommendationsResponse> recommend(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long groupId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String cursor) {
        return ApiResponse.success("여행지 후보를 추천했습니다.",
                recommendationService.recommend(authUser.id(), groupId, limit, category, sort, cursor));
    }

    /** 2차 추천 - 기준 여행지 주변 */
    @GetMapping("/{placeId}/recommendations")
    public ApiResponse<NearbyDestinationsResponse> recommendNearby(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long groupId,
            @PathVariable String placeId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(name = "radius_meters", required = false) Integer radiusMeters,
            @RequestParam(required=false) String category,
            @RequestParam(required=false) String cursor) {
        return ApiResponse.success("주변 여행지를 추천했습니다.",
                recommendationService.recommendNearby(authUser.id(), groupId, placeId, limit, radiusMeters, category, cursor));
    }

    /** 여행지 선택 저장 */
    @PutMapping("/selection")
    public ApiResponse<SaveDestinationSelectionResponse> saveSelection(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long groupId,
            @Valid @RequestBody SaveDestinationSelectionRequest request) {
        return ApiResponse.success("여행지 선택이 저장되었습니다.",
                selectionService.save(authUser.id(), groupId, request));
    }
}
