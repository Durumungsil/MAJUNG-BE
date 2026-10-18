package durumungsil.majung.controller;

import durumungsil.majung.dto.group.response.GroupMembersResponse;
import org.springframework.web.bind.annotation.RequestHeader;
import durumungsil.majung.global.response.ApiResponse;
import durumungsil.majung.service.GroupMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/members")
@RequiredArgsConstructor
public class GroupMemberController {

    private final GroupMemberService groupMemberService;

    /** 그룹 멤버 목록 */
    @GetMapping
    public ApiResponse<GroupMembersResponse> getMembers(@RequestHeader("X-User-Id") Long userId, @PathVariable Long groupId) {
        return ApiResponse.success("그룹 멤버를 조회했습니다.", groupMemberService.getMembers(userId, groupId));
    }

    /** 그룹 탈퇴 - 204 는 바디를 보낼 수 없어서 공통 응답 형식을 유지하려고 200 으로 응답 */
    @DeleteMapping("/me")
    public ApiResponse<Void> leaveGroup(@RequestHeader("X-User-Id") Long userId, @PathVariable Long groupId) {
        groupMemberService.leaveGroup(userId, groupId);
        return ApiResponse.success("그룹에서 탈퇴했습니다.");
    }
}
