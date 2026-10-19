package durumungsil.majung.controller;

import durumungsil.majung.dto.group.request.JoinGroupRequest;
import durumungsil.majung.dto.group.response.InviteCodeResponse;
import durumungsil.majung.dto.group.response.JoinGroupResponse;
import durumungsil.majung.dto.group.response.VerifyInviteCodeResponse;
import org.springframework.web.bind.annotation.RequestHeader;
import durumungsil.majung.global.response.ApiResponse;
import durumungsil.majung.service.GroupInvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupInvitationController {

    private final GroupInvitationService groupInvitationService;

    /** 그룹 초대 코드 조회 */
    @GetMapping("/{groupId}/code")
    public ApiResponse<InviteCodeResponse> getInviteCode(@RequestHeader("X-User-Id") Long userId, @PathVariable Long groupId) {
        return ApiResponse.success("그룹 정보를 조회했습니다.", groupInvitationService.getInviteCode(userId, groupId));
    }

    /** 초대 코드 검증 */
    @GetMapping("/invitations/{inviteCode}")
    public ApiResponse<VerifyInviteCodeResponse> verifyInviteCode(
            @RequestHeader("X-User-Id") Long userId, @PathVariable String inviteCode) {
        return ApiResponse.success("유효한 초대 코드입니다.", groupInvitationService.verifyInviteCode(userId, inviteCode));
    }

    /** 그룹 참여 */
    @PostMapping("/{groupId}/invitations")
    public ResponseEntity<ApiResponse<JoinGroupResponse>> joinGroup(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long groupId,
            @Valid @RequestBody JoinGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("그룹에 참여했습니다.",
                        groupInvitationService.joinGroup(userId, groupId, request.inviteCode())));
    }
}
