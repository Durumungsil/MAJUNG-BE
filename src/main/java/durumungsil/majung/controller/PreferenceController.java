package durumungsil.majung.controller;

import durumungsil.majung.dto.preference.request.SavePreferenceRequest;
import durumungsil.majung.dto.preference.response.MyPreferenceResponse;
import durumungsil.majung.dto.preference.response.PreferenceSummaryResponse;
import durumungsil.majung.dto.preference.response.SavePreferenceResponse;
import org.springframework.web.bind.annotation.RequestHeader;
import durumungsil.majung.global.response.ApiResponse;
import durumungsil.majung.service.PreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/preferences")
@RequiredArgsConstructor
public class PreferenceController {

    private final PreferenceService preferenceService;

    /** 그룹 의견 수집 */
    @PutMapping
    public ApiResponse<SavePreferenceResponse> savePreference(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long groupId,
            @Valid @RequestBody SavePreferenceRequest request) {
        return ApiResponse.success("여행 취향이 저장되었습니다.", preferenceService.savePreference(userId, groupId, request));
    }

    /** 내 취향 조회 */
    @GetMapping("/me")
    public ApiResponse<MyPreferenceResponse> getMyPreference(@RequestHeader("X-User-Id") Long userId, @PathVariable Long groupId) {
        return ApiResponse.success("내 취향 정보를 조회했습니다.", preferenceService.getMyPreference(userId, groupId));
    }

    /** 취향 통합 */
    @GetMapping("/summary")
    public ApiResponse<PreferenceSummaryResponse> getSummary(@RequestHeader("X-User-Id") Long userId, @PathVariable Long groupId) {
        return ApiResponse.success("그룹 취향을 조회했습니다.", preferenceService.getSummary(userId, groupId));
    }
}
