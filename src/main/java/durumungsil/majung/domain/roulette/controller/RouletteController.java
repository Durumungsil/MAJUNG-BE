package durumungsil.majung.domain.roulette.controller;

import durumungsil.majung.domain.roulette.dto.request.DrawRouletteRequest;
import durumungsil.majung.domain.roulette.dto.response.DrawRouletteResponse;
import durumungsil.majung.domain.roulette.dto.response.RouletteResultResponse;
import durumungsil.majung.domain.roulette.entity.RouletteType;
import durumungsil.majung.domain.roulette.service.RouletteService;
import durumungsil.majung.global.response.ApiResponse;
import durumungsil.majung.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/groups/{groupId}/roulette")
@RequiredArgsConstructor
public class RouletteController {
    private final RouletteService service;

    @PostMapping("/destinations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DrawRouletteResponse> drawDestination(@AuthenticationPrincipal AuthUser user,
                                                            @PathVariable Long groupId,
                                                            @Valid @RequestBody DrawRouletteRequest request) {
        return ApiResponse.success("여행지를 추첨했습니다.",
                service.draw(user.id(), groupId, RouletteType.DESTINATION, request));
    }

    @PostMapping("/restaurants")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DrawRouletteResponse> drawRestaurant(@AuthenticationPrincipal AuthUser user,
                                                           @PathVariable Long groupId,
                                                           @Valid @RequestBody DrawRouletteRequest request) {
        return ApiResponse.success("식당을 추첨했습니다.",
                service.draw(user.id(), groupId, RouletteType.RESTAURANT, request));
    }

    @GetMapping("/{rouletteId}")
    public ApiResponse<RouletteResultResponse> get(@AuthenticationPrincipal AuthUser user,
                                                  @PathVariable Long groupId,
                                                  @PathVariable Long rouletteId) {
        return ApiResponse.success("룰렛 결과를 조회했습니다.", service.get(user.id(), groupId, rouletteId));
    }
}
