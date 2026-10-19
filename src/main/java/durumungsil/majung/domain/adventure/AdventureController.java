package durumungsil.majung.domain.adventure;

import durumungsil.majung.global.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import static durumungsil.majung.domain.adventure.AdventureDtos.*;

@RestController
@RequestMapping("/api/v1/groups/{groupId}")
@RequiredArgsConstructor
public class AdventureController {
    private final AdventureService service;

    @PostMapping("/adventure/process")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Created> create(@PathVariable Long groupId, @RequestBody CreateRequest request) {
        return ApiResponse.ok("모험 커리큘럼이 생성되었습니다.", service.create(groupId, request));
    }

    @GetMapping("/adventure/process/{processId}")
    public ApiResponse<ProcessView> progress(@PathVariable Long groupId, @PathVariable Long processId) {
        return ApiResponse.ok("모험 커리큘럼을 조회했습니다.", service.progress(groupId, processId));
    }

    @GetMapping("/missions/current")
    public ApiResponse<CurrentMission> current(@PathVariable Long groupId) {
        return ApiResponse.ok("현재 미션을 조회했습니다.", service.current(groupId));
    }

    @PostMapping("/missions/{missionId}/start")
    public ApiResponse<Started> start(@PathVariable Long groupId, @PathVariable Long missionId,
                                      @RequestBody StartRequest request) {
        return ApiResponse.ok("미션을 시작했습니다.", service.start(groupId, missionId, request));
    }

    @GetMapping("/missions/{missionId}/hint")
    public ApiResponse<Hint> hint(@PathVariable Long groupId, @PathVariable Long missionId) {
        return ApiResponse.ok("힌트를 제공했습니다.", service.hint(groupId, missionId));
    }

    @PostMapping("/missions/{missionId}/complete")
    public ApiResponse<Completed> complete(@PathVariable Long groupId, @PathVariable Long missionId,
                                          @RequestBody CompleteRequest request) {
        return ApiResponse.ok("미션을 완료했습니다.", service.complete(groupId, missionId, request));
    }
}
