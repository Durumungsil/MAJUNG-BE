package durumungsil.majung;

import durumungsil.majung.domain.group.*;
import durumungsil.majung.domain.preference.Preference;
import durumungsil.majung.domain.store.*;
import durumungsil.majung.domain.trip.*;
import durumungsil.majung.domain.user.User;
import durumungsil.majung.global.security.jwt.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 모험모드 PR과 함께 구성할 때 인증 → 그룹 권한 → 미션 상태 전이를 검증한다. */
@EnabledIf("adventureAvailable")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:majung-auth-adventure;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class AdventureAuthIntegrationTests {
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired JwtTokenProvider tokens;
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper mapper;

    static boolean adventureAvailable() {
        return AdventureAuthIntegrationTests.class.getClassLoader()
                .getResource("durumungsil/majung/domain/adventure/AdventureController.class") != null;
    }

    @Test
    void onlyTheAuthenticatedGroupMemberCanRunTheAdventureFlow() throws Exception {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Seoul"));
        long[] ids = tx.execute(status -> {
            User owner = User.builder().googleSub(UUID.randomUUID().toString()).build();
            User outsider = User.builder().googleSub(UUID.randomUUID().toString()).build();
            em.persist(owner);
            em.persist(outsider);
            Group group = Group.builder().groupName("모험").owner(owner).memberCount(1).maxMembers(4).build();
            em.persist(group);
            em.persist(GroupMember.builder().group(group).user(owner).build());
            Trip trip = Trip.builder().group(group).region("BUSAN").mode(TripMode.ADVENTURE)
                    .travelStartDate(date).travelEndDate(date.plusDays(1)).build();
            em.persist(trip);
            em.persist(Preference.builder().trip(trip).user(owner).allergies("[]").budgetMax(100000).build());
            em.persist(Store.builder().destinationName("미션 장소").destinationType(Type.ART_SPORTS)
                    .sido("부산광역시").latitude(35.0).longitude(129.0).build());
            return new long[]{owner.getId(), outsider.getId(), group.getId()};
        });
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String base = "/api/v1/groups/" + ids[2];
        String ownerToken = "Bearer " + tokens.createAccessToken(ids[0]);
        String outsiderToken = "Bearer " + tokens.createAccessToken(ids[1]);
        mvc.perform(get(base + "/missions/current")).andExpect(status().isUnauthorized());
        mvc.perform(get(base + "/missions/current").header("Authorization", outsiderToken)
                        .header("X-User-Id", ids[0])).andExpect(status().isForbidden());
        String created = mvc.perform(post(base + "/adventure/process").header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"travel_date\":\"" + date + "\",\"mission_count\":1}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long process = mapper.readTree(created).path("data").path("process_id").asLong();
        long mission = mapper.readTree(created).path("data").path("first_mission_id").asLong();
        mvc.perform(get(base + "/adventure/process/" + process).header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.missions[0].missionId").value(mission));
        String missionPath = base + "/missions/" + mission;
        mvc.perform(post(missionPath + "/complete").header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"latitude\":35,\"longitude\":129}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code").value("409"));
        mvc.perform(post(missionPath + "/start").header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"latitude\":35,\"longitude\":129}"))
                .andExpect(status().isOk());
        mvc.perform(get(missionPath + "/hint").header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.hint_count").value(1));
        mvc.perform(post(missionPath + "/complete").header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"latitude\":35,\"longitude\":129}"))
                .andExpect(status().isOk());
        mvc.perform(get(base + "/adventure/process/" + process).header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.missions[0].completedAt").exists());
    }
}
