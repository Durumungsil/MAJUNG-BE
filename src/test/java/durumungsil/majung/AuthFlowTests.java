package durumungsil.majung;

import com.jayway.jsonpath.JsonPath;
import durumungsil.majung.global.exception.BusinessException;
import durumungsil.majung.global.exception.ErrorCode;
import durumungsil.majung.global.security.google.GoogleIdTokenVerifier;
import durumungsil.majung.global.security.google.GoogleUserInfo;
import durumungsil.majung.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 구글 로그인 → JWT 발급 → 컨트롤러에서 유저 검증까지 전체 흐름.
 * 구글 서버와 통신하지 않도록 GoogleIdTokenVerifier 만 mock 한다.
 */
@SpringBootTest
class AuthFlowTests {

    @Autowired
    WebApplicationContext context;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    GoogleIdTokenVerifier googleIdTokenVerifier;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void 토큰_없이_보호된_API를_호출하면_401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error_code").value("401"))
                .andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.getMessage()));

        mockMvc.perform(get("/api/v1/groups"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 잘못된_토큰이면_401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_TOKEN.getMessage()));
    }

    @Test
    void 구글_로그인하면_토큰이_발급되고_내_정보를_조회할_수_있다() throws Exception {
        given(googleIdTokenVerifier.verify("google-token-1"))
                .willReturn(new GoogleUserInfo("google-sub-1", "user1@gmail.com", "현태"));

        String body = mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id_token\":\"google-token-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.first_login").value(true))
                .andExpect(jsonPath("$.data.token_type").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(body, "$.data.access_token");
        Number userId = JsonPath.read(body, "$.data.user_id");

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user_id").value(userId.longValue()))
                .andExpect(jsonPath("$.data.email").value("user1@gmail.com"))
                .andExpect(jsonPath("$.data.user_name").value("현태"));

        // 같은 구글 계정으로 다시 로그인하면 같은 유저
        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id_token\":\"google-token-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.first_login").value(false))
                .andExpect(jsonPath("$.data.user_id").value(userId.longValue()));
    }

    @Test
    void 토큰의_유저로_컨트롤러가_동작한다() throws Exception {
        given(googleIdTokenVerifier.verify("google-token-2"))
                .willReturn(new GoogleUserInfo("google-sub-2", "user2@gmail.com", "유저2"));
        String body = login("google-token-2");
        String accessToken = JsonPath.read(body, "$.data.access_token");

        // X-User-Id 헤더 없이 토큰만으로 내 그룹 목록 조회
        mockMvc.perform(get("/api/v1/groups").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void 존재하지_않는_유저의_토큰이면_404() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken(999_999L);

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(ErrorCode.USER_NOT_FOUND.getMessage()));
    }

    @Test
    void 구글_토큰_검증에_실패하면_401() throws Exception {
        given(googleIdTokenVerifier.verify("bad-google-token"))
                .willThrow(new BusinessException(ErrorCode.INVALID_GOOGLE_TOKEN));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id_token\":\"bad-google-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_GOOGLE_TOKEN.getMessage()));
    }

    @Test
    void 리프레시_토큰은_한_번만_쓸_수_있고_로그아웃하면_폐기된다() throws Exception {
        given(googleIdTokenVerifier.verify("google-token-3"))
                .willReturn(new GoogleUserInfo("google-sub-3", "user3@gmail.com", "유저3"));
        String loginBody = login("google-token-3");
        String oldRefresh = JsonPath.read(loginBody, "$.data.refresh_token");

        String refreshBody = refresh(oldRefresh)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newAccess = JsonPath.read(refreshBody, "$.data.access_token");
        String newRefresh = JsonPath.read(refreshBody, "$.data.refresh_token");

        // 이미 쓴 refresh 토큰 재사용 불가
        refresh(oldRefresh)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_REFRESH_TOKEN.getMessage()));

        // 로그아웃 후에는 새 refresh 토큰도 사용 불가
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + newAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"" + newRefresh + "\"}"))
                .andExpect(status().isOk());
        refresh(newRefresh).andExpect(status().isUnauthorized());
    }

    private String login(String idToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id_token\":\"" + idToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void 다른_그룹의_모험_API는_인증된_사용자도_호출할_수_없다() throws Exception {
        given(googleIdTokenVerifier.verify("outsider-token"))
                .willReturn(new GoogleUserInfo("outsider-sub", "outsider@gmail.com", "외부인"));
        String token = JsonPath.read(login("outsider-token"), "$.data.access_token");
        for (String path : new String[]{"/adventure/process/1", "/missions/current", "/missions/1/hint"}) {
            mockMvc.perform(get("/api/v1/groups/999999" + path).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/groups/999999" + path)).andExpect(status().isUnauthorized());
        }
        for (String path : new String[]{"/adventure/process", "/missions/1/start", "/missions/1/complete"}) {
            mockMvc.perform(post("/api/v1/groups/999999" + path).header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refresh_token\":\"" + refreshToken + "\"}"));
    }
}
