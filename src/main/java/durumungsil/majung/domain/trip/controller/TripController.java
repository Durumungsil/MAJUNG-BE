package durumungsil.majung.domain.trip.controller;

import durumungsil.majung.domain.trip.dto.request.CreateTripRequest;
import durumungsil.majung.domain.trip.dto.response.TripListResponse;
import durumungsil.majung.domain.trip.dto.response.TripResponse;
import durumungsil.majung.global.response.ApiResponse;
import durumungsil.majung.global.security.AuthUser;
import durumungsil.majung.domain.trip.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    /** 여행 회차 생성 */
    @PostMapping
    public ResponseEntity<ApiResponse<TripResponse>> createTrip(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long groupId,
            @Valid @RequestBody CreateTripRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("새 여행이 만들어졌습니다.", tripService.createTrip(authUser.id(), groupId, request)));
    }

    /** 여행 회차 목록 */
    @GetMapping
    public ApiResponse<TripListResponse> getTrips(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long groupId) {
        return ApiResponse.success("여행 목록을 조회했습니다.", tripService.getTrips(authUser.id(), groupId));
    }
}
