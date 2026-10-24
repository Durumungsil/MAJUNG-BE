package durumungsil.majung;

import durumungsil.majung.domain.group.entity.*;
import durumungsil.majung.domain.preference.entity.*;
import durumungsil.majung.domain.trip.entity.*;
import durumungsil.majung.domain.trip.repository.*;
import durumungsil.majung.domain.user.entity.User;
import durumungsil.majung.domain.group.dto.request.UpdateGroupRequest;
import durumungsil.majung.domain.preference.dto.request.SavePreferenceRequest;
import durumungsil.majung.domain.group.repository.GroupRepository;
import durumungsil.majung.domain.auth.service.*;
import durumungsil.majung.domain.group.service.*;
import durumungsil.majung.domain.preference.service.*;
import durumungsil.majung.domain.story.service.*;
import durumungsil.majung.domain.trip.service.*;
import durumungsil.majung.domain.user.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class GroupRegressionTests {
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired GroupRepository groups;
    @Autowired GroupService groupService;
    @Autowired GroupInvitationService invitations;
    @Autowired PreferenceService preferences;
    @Autowired WebApplicationContext context;

    record Fixture(long owner, long group) { }

    Fixture seed() {
        return tx.execute(status -> {
            User user = User.builder().googleSub(UUID.randomUUID().toString()).userName("테스터").build();
            em.persist(user);
            Group group = Group.builder().groupName("그룹").owner(user).memberCount(1).maxMembers(4)
                    .inviteCode("abc123").build();
            // 각 테스트에서 독립적인 초대 코드를 사용한다.
            group.issueInviteCode(UUID.randomUUID().toString(), LocalDateTime.now().minusDays(2));
            em.persist(group);
            em.persist(GroupMember.builder().group(group).user(user).build());
            em.persist(Trip.builder().group(group).region("BUSAN").travelStartDate(LocalDate.now())
                    .travelEndDate(LocalDate.now().plusDays(1)).build());
            return new Fixture(user.getId(), group.getId());
        });
    }

    @Test
    void collectionOnlyPreferenceChangeUpdatesThePersistedTimestamp() {
        Fixture data = seed();
        preferences.savePreference(data.owner(), data.group(), preference("BUS"));
        LocalDateTime previous = LocalDateTime.of(2000, 1, 1, 0, 0);
        jdbc.update("update preferences set updated_at = ? where user_id = ?", previous, data.owner());

        preferences.savePreference(data.owner(), data.group(), preference("WALK"));

        LocalDateTime saved = jdbc.queryForObject("select updated_at from preferences where user_id = ?",
                LocalDateTime.class, data.owner());
        assertTrue(saved.isAfter(previous));
        assertEquals(List.of("WALK"), jdbc.queryForList("select transport_type from preference_transport_types "
                + "where preference_id = (select id from preferences where user_id = ?)", String.class, data.owner()));
    }

    @Test
    void nullFoodItemIsBadRequestRatherThanServerError() throws Exception {
        Fixture data = seed();
        // 이 테스트는 인증 필터와 분리해 실제 MVC 입력 검증을 확인한다.
        MockMvcBuilders.webAppContextSetup(context).build().perform(
                put("/api/v1/groups/" + data.group() + "/preferences")
                        .header("X-User-Id", data.owner()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transport_types\":[\"BUS\"],\"food_preferences\":[null],"
                                + "\"allergies\":[],\"travel_categories\":[\"FOOD\"],\"budget\":{\"max\":10000}}"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"settings", "invite"})
    void groupMutationCannotOverwriteAConcurrentMembershipChange(String action) throws Exception {
        Fixture data = seed();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch mutationStarted = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> membership = pool.submit(() -> tx.executeWithoutResult(status -> {
                Group group = groups.findByIdForUpdate(data.group()).orElseThrow();
                locked.countDown();
                await(release);
                User next = User.builder().googleSub(UUID.randomUUID().toString()).build();
                em.persist(next);
                em.persist(GroupMember.builder().group(group).user(next).build());
                group.increaseMemberCount();
            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            Future<?> mutation = pool.submit(() -> {
                mutationStarted.countDown();
                if (action.equals("settings")) {
                    groupService.updateGroup(data.owner(), data.group(),
                            new UpdateGroupRequest("수정", null, null, null, null, null));
                } else {
                    invitations.getInviteCode(data.owner(), data.group());
                }
            });
            try {
                assertTrue(mutationStarted.await(10, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> mutation.get(300, TimeUnit.MILLISECONDS));
            } finally {
                release.countDown();
            }
            membership.get(10, TimeUnit.SECONDS);
            mutation.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
        assertEquals(2, jdbc.queryForObject("select member_count from groups where id = ?", Integer.class, data.group()));
        assertEquals(2, jdbc.queryForObject("select count(*) from group_members where group_id = ?", Integer.class, data.group()));
    }

    private static SavePreferenceRequest preference(String transport) {
        return new SavePreferenceRequest(List.of(transport), List.of(), List.of(), List.of("FOOD"),
                new SavePreferenceRequest.Budget(10000));
    }

    @Test
    void mvcRoutingErrorsKeepTheirStatus() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/missing"))
                .andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/groups/1/preferences"))
                .andExpect(status().isMethodNotAllowed());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("잠금 대기 시간 초과");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
