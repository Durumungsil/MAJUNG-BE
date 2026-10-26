package durumungsil.majung.domain.roulette.controller;

import durumungsil.majung.domain.roulette.dto.response.MinimumConstraintsResponse;
import durumungsil.majung.domain.roulette.service.MinimumConstraintsService;
import durumungsil.majung.global.response.ApiResponse;
import durumungsil.majung.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/groups/{groupId}/constraints/minimum")
@RequiredArgsConstructor
public class MinimumConstraintsController {
    private final MinimumConstraintsService service;

    @GetMapping
    public ApiResponse<MinimumConstraintsResponse> get(@AuthenticationPrincipal AuthUser user,
                                                       @PathVariable Long groupId) {
        return ApiResponse.success("최소 조건을 조회했습니다.", service.get(user.id(), groupId));
    }
}
