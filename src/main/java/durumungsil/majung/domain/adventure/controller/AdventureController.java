package durumungsil.majung.domain.adventure.controller;

import durumungsil.majung.domain.adventure.service.AdventureService;
import durumungsil.majung.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import static durumungsil.majung.domain.adventure.dto.AdventureDtos.*;

@RestController
@RequestMapping("/api/v1/groups/{groupId}")
@RequiredArgsConstructor
public class AdventureController {
    private final AdventureService service;

    @PostMapping("/adventure/process")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Created> create(@PathVariable Long groupId, @RequestBody CreateRequest request) {
        return ApiResponse.success("모험 커리큘럼이 생성되었습니다.", service.create(groupId, request));
    }

    @GetMapping("/adventure/process/{processId}")
    public ApiResponse<ProcessView> progress(@PathVariable Long groupId, @PathVariable Long processId) {
        return ApiResponse.success("모험 커리큘럼을 조회했습니다.", service.progress(groupId, processId));
    }

    @GetMapping("/missions/current")
    public ApiResponse<CurrentMission> current(@PathVariable Long groupId) {
        return ApiResponse.success("현재 미션을 조회했습니다.", service.current(groupId));
    }

    @PostMapping("/missions/{missionId}/start")
    public ApiResponse<Started> start(@PathVariable Long groupId, @PathVariable Long missionId,
                                      @RequestBody StartRequest request) {
        return ApiResponse.success("미션을 시작했습니다.", service.start(groupId, missionId, request));
    }

    @GetMapping("/missions/{missionId}/hint")
    public ApiResponse<Hint> hint(@PathVariable Long groupId, @PathVariable Long missionId) {
        return ApiResponse.success("힌트를 제공했습니다.", service.hint(groupId, missionId));
    }

    @PostMapping("/missions/{missionId}/distance")
    public ApiResponse<DistanceResponse> distance(@PathVariable Long groupId, @PathVariable Long missionId,
                                                  @RequestBody DistanceRequest request) {
        return ApiResponse.success("목적지까지 거리를 조회했습니다.", service.distance(groupId, missionId, request));
    }

    @PostMapping("/missions/{missionId}/replace")
    public ApiResponse<CurrentMission> replace(@PathVariable Long groupId, @PathVariable Long missionId,
                                               @RequestBody ReplaceRequest request) {
        return ApiResponse.success("로드뷰가 없는 미션을 교체했습니다. 다시 시작해주세요.", service.replace(groupId, missionId, request));
    }

    @PostMapping("/missions/{missionId}/complete")
    public ApiResponse<Completed> complete(@PathVariable Long groupId, @PathVariable Long missionId,
                                          @RequestBody CompleteRequest request) {
        return ApiResponse.success("미션을 완료했습니다.", service.complete(groupId, missionId, request));
    }
}
