package durumungsil.majung;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class GroupSecurityTests {
    @Autowired WebApplicationContext context;

    @Test
    void identityHeaderDoesNotGrantAccessOutsideLocalProfile() throws Exception {
        MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()
                .perform(get("/api/v1/groups").header("X-User-Id", "1"))
                .andExpect(result -> assertTrue(result.getResponse().getStatus() == 401
                        || result.getResponse().getStatus() == 403));
    }
}
