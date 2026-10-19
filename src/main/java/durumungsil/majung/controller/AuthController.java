package durumungsil.majung.controller;

import durumungsil.majung.dto.auth.request.GoogleLoginRequest;
import durumungsil.majung.dto.auth.request.RefreshTokenRequest;
import durumungsil.majung.dto.auth.response.LoginResponse;
import durumungsil.majung.dto.auth.response.TokenResponse;
import durumungsil.majung.global.response.ApiResponse;
import durumungsil.majung.global.security.AuthUser;
import durumungsil.majung.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 구글 로그인 - 앱에서 받은 구글 idToken 으로 로그인 (처음이면 회원가입) */
    @PostMapping("/google")
    public ApiResponse<LoginResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return ApiResponse.success("로그인되었습니다.", authService.loginWithGoogle(request.idToken()));
    }

    /** 토큰 재발급 */
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success("토큰이 재발급되었습니다.", authService.refresh(request.refreshToken()));
    }

    /** 로그아웃 */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(authUser.id(), request.refreshToken());
        return ApiResponse.success("로그아웃되었습니다.");
    }
}
