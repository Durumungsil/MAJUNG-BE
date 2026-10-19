package durumungsil.majung.global.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** local을 명시하지 않은 기본 환경에서는 인증 우회 경로가 열리면 안 된다. */
@SpringBootTest
@AutoConfigureMockMvc
class AdventureSecurityTests {
    @Autowired MockMvc mvc;

    @Test
    void allSixRoutesAreDeniedOutsideLocalEvenForAuthenticatedUsers() throws Exception {
        String base = "/api/v1/groups/1";
        String[][] routes = {
                {"POST", "/adventure/process"}, {"GET", "/adventure/process/1"},
                {"GET", "/missions/current"}, {"POST", "/missions/1/start"},
                {"GET", "/missions/1/hint"}, {"POST", "/missions/1/complete"}
        };
        for (String[] route : routes) {
            mvc.perform(request(HttpMethod.valueOf(route[0]), base + route[1])
                            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(request(HttpMethod.valueOf(route[0]), base + route[1])
                            .with(user("reviewer")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void containerErrorsAreRenderedWhileDirectErrorAndOtherRequestsStayDenied() throws Exception {
        mvc.perform(get("/error").with(request -> { request.setDispatcherType(DispatcherType.ERROR); return request; })
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/groups/1/missions/current")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.status").value(500));
        mvc.perform(get("/error")).andExpect(status().isForbidden());
        mvc.perform(get("/unmatched")).andExpect(status().isForbidden());
    }
}
