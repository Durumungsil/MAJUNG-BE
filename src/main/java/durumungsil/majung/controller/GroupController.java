package durumungsil.majung.controller;

import durumungsil.majung.dto.group.request.CreateGroupRequest;
import durumungsil.majung.dto.group.request.SelectModeRequest;
import durumungsil.majung.dto.group.request.UpdateGroupRequest;
import durumungsil.majung.dto.group.response.CreateGroupResponse;
import durumungsil.majung.dto.group.response.GroupDetailResponse;
import durumungsil.majung.dto.group.response.GroupListResponse;
import durumungsil.majung.dto.group.response.SelectModeResponse;
import durumungsil.majung.dto.group.response.UpdateGroupResponse;
import jakarta.validation.Valid;
import durumungsil.majung.global.response.ApiResponse;
import durumungsil.majung.global.security.AuthUser;
import durumungsil.majung.service.GroupService;
import durumungsil.majung.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final TripService tripService;

    /** 그룹 생성 */
    @PostMapping
    public ResponseEntity<ApiResponse<CreateGroupResponse>> createGroup(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody CreateGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("그룹이 생성되었습니다.", groupService.createGroup(authUser.id(), request)));
    }

    /** 그룹 목록 조회 */
    @GetMapping
    public ApiResponse<GroupListResponse> getMyGroups(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.success("그룹 목록을 조회했습니다.", groupService.getMyGroups(authUser.id()));
    }

    /** 그룹 상세 조회 */
    @GetMapping("/{groupId}")
    public ApiResponse<GroupDetailResponse> getGroup(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long groupId) {
        return ApiResponse.success("그룹 정보를 조회했습니다.", groupService.getGroup(authUser.id(), groupId));
    }

    /** 그룹 설정 수정 */
    @PatchMapping("/{groupId}")
    public ApiResponse<UpdateGroupResponse> updateGroup(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long groupId,
            @Valid @RequestBody UpdateGroupRequest request) {
        return ApiResponse.success("그룹 설정이 수정되었습니다.", groupService.updateGroup(authUser.id(), groupId, request));
    }

    /** 모드 선택 */
    @PatchMapping("/{groupId}/mode")
    public ApiResponse<SelectModeResponse> selectMode(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long groupId,
            @Valid @RequestBody SelectModeRequest request) {
        return ApiResponse.success("그룹 모드가 확정되었습니다.", tripService.selectMode(authUser.id(), groupId, request.mode()));
    }
}
