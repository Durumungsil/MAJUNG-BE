package durumungsil.majung.domain.adventure;

import durumungsil.majung.domain.group.Group;
import durumungsil.majung.domain.group.GroupMember;
import durumungsil.majung.domain.preference.Preference;
import durumungsil.majung.domain.store.Store;
import durumungsil.majung.domain.store.Type;
import durumungsil.majung.domain.trip.Trip;
import durumungsil.majung.domain.trip.TripMode;
import durumungsil.majung.domain.user.User;
import durumungsil.majung.global.api.ApiException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AdventureApiTests {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 19);
    private static final LocalDateTime START = DATE.atTime(1, 0);
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired AdventureService service;
    @MockitoBean AdventureSettings settings;
    private long groupId;

    @BeforeEach
    void setup() {
        for (String table : List.of("missions", "adventure_processes", "preference_foods", "preference_transport_types",
                "preferences", "trip_constraints", "group_members", "trips", "groups", "users",
                "store_opening_hours", "store_amenities", "stores")) {
            jdbc.update("delete from " + table);
        }
        when(settings.today()).thenReturn(DATE);
        when(settings.nowUtc()).thenReturn(START);
        when(settings.hintViewSeconds()).thenReturn(30);
        when(settings.arrivalRadiusMeters()).thenReturn(100.0);
        when(settings.maxMissionCount()).thenReturn(20);
        groupId = seedGroup();
    }

    @Test
    void sixApisCompleteTheCurriculumWithoutLeakingDestinationInformation() throws Exception {
        JsonNode created = create(3);
        long processId = created.path("process_id").asLong();
        long first = created.path("first_mission_id").asLong();
        assertEquals("READY", created.path("status").asString());
        assertEquals(3, created.path("destination_count").asInt());
        assertTrue(created.path("created_at").asString().endsWith("Z"));
        mvc.perform(post(base() + "/adventure/process").contentType(MediaType.APPLICATION_JSON)
                .content(createBody(3))).andExpect(status().isConflict());

        String current = mvc.perform(get(base() + "/missions/current")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.started_at").value(org.hamcrest.Matchers.nullValue()))
                .andReturn().getResponse().getContentAsString();
        for (String secret : List.of("latitude", "longitude", "store", "destination", "정답")) {
            assertFalse(current.contains(secret), current);
        }
        JsonNode initial = data(mvc.perform(get(base() + "/adventure/process/" + processId))
                .andExpect(status().isOk()).andReturn());
        long second = initial.path("missions").get(1).path("missionId").asLong();
        assertEquals("LOCKED", initial.path("missions").get(1).path("status").asString());
        mvc.perform(post(base() + "/missions/" + second + "/start").contentType(MediaType.APPLICATION_JSON)
                .content(position())).andExpect(status().isConflict());
        mvc.perform(get(base() + "/missions/" + second + "/hint")).andExpect(status().isConflict());
        mvc.perform(post(base() + "/missions/" + first + "/complete").contentType(MediaType.APPLICATION_JSON)
                .content(position())).andExpect(status().isConflict());

        mvc.perform(post(base() + "/missions/" + first + "/start").contentType(MediaType.APPLICATION_JSON)
                .content("{\"latitude\":91,\"longitude\":129}")).andExpect(status().isBadRequest());
        String firstStarted = start(first).path("started_at").asString();
        when(settings.nowUtc()).thenReturn(START.plusMinutes(1));
        JsonNode restarted = data(mvc.perform(post(base() + "/missions/" + first + "/start")
                .contentType(MediaType.APPLICATION_JSON).content("{\"latitude\":36,\"longitude\":128}"))
                .andExpect(status().isOk()).andReturn());
        assertEquals(firstStarted, restarted.path("started_at").asString());
        mvc.perform(get(base() + "/missions/" + first + "/hint")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hint_count").value(1)).andExpect(jsonPath("$.data.view_seconds").value(30))
                .andExpect(jsonPath("$.data.latitude").value(35.0));
        mvc.perform(get(base() + "/missions/" + first + "/hint")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hint_count").value(2));
        mvc.perform(post(base() + "/missions/" + first + "/complete").contentType(MediaType.APPLICATION_JSON)
                .content("{\"latitude\":36,\"longitude\":128}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error_code").value("400"));
        assertEquals("AVAILABLE", jdbc.queryForObject("select status from missions where id = ?", String.class, first));
        mvc.perform(post(base() + "/missions/" + first + "/complete").contentType(MediaType.APPLICATION_JSON)
                .content("{\"latitude\":35,\"longitude\":129,\"completed_at\":\"2099-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest());
        JsonNode completed = complete(first);
        assertEquals(second, completed.path("next_missionId").asLong());
        assertEquals("A", jdbc.queryForObject("select clear_rank from missions where id = ?", String.class, first));
        assertEquals(35.0, jdbc.queryForObject("select start_latitude from missions where id = ?", Double.class, first));
        mvc.perform(get(base() + "/adventure/process/" + processId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.progress.completed").value(1))
                .andExpect(jsonPath("$.data.current_missionId").value(second));
        for (int i = 1; i < 3; i++) {
            long mission = initial.path("missions").get(i).path("missionId").asLong();
            start(mission);
            complete(mission);
        }
        mvc.perform(get(base() + "/adventure/process/" + processId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.progress.completed").value(3))
                .andExpect(jsonPath("$.data.current_missionId").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get(base() + "/missions/current")).andExpect(status().isNotFound());
        mvc.perform(post(base() + "/missions/" + first + "/complete").contentType(MediaType.APPLICATION_JSON)
                .content(position())).andExpect(status().isConflict());
        mvc.perform(post(base() + "/missions/" + first + "/start").contentType(MediaType.APPLICATION_JSON)
                .content(position())).andExpect(status().isConflict());
        mvc.perform(get(base() + "/missions/" + first + "/hint")).andExpect(status().isConflict());
    }

    @Test
    void validatesBodiesDatesCountsAndResourceScope() throws Exception {
        for (String body : List.of("{}", "null", "{", createBody(0), createBody(21),
                "{\"travel_date\":\"2026-10-21\",\"mission_count\":1}",
                "{\"travel_date\":\"invalid\",\"mission_count\":1}")) {
            mvc.perform(post(base() + "/adventure/process").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        JsonNode created = create(1);
        long mission = created.path("first_mission_id").asLong();
        long otherGroup = seedGroup();
        String other = "/api/v1/groups/" + otherGroup;
        mvc.perform(get(other + "/adventure/process/" + created.path("process_id").asLong()))
                .andExpect(status().isNotFound());
        mvc.perform(get(other + "/missions/" + mission + "/hint")).andExpect(status().isNotFound());
        mvc.perform(post(other + "/missions/" + mission + "/start").contentType(MediaType.APPLICATION_JSON)
                .content(position())).andExpect(status().isNotFound());
        mvc.perform(post(other + "/missions/" + mission + "/complete").contentType(MediaType.APPLICATION_JSON)
                .content(position())).andExpect(status().isNotFound());
        mvc.perform(get(base() + "/adventure/process/not-a-number")).andExpect(status().isBadRequest());
        // 열외 엔드포인트에는 공개 보안 설정과 컨트롤러를 만들지 않는다.
        mvc.perform(patch(base() + "/missions/" + mission + "/route-choice").contentType(MediaType.APPLICATION_JSON)
                .content("{\"route_type\":\"FUN\"}")).andExpect(status().isForbidden());
        mvc.perform(post(base() + "/missions/" + mission + "/verify").contentType(MediaType.APPLICATION_JSON)
                .content(position())).andExpect(status().isForbidden());
    }

    @Test
    void currentMissionUsesKoreanCalendarAndCannotStartOtherDays() throws Exception {
        mvc.perform(post(base() + "/adventure/process").contentType(MediaType.APPLICATION_JSON)
                .content("{\"travel_date\":\"2026-10-20\",\"mission_count\":1}"))
                .andExpect(status().isCreated());
        mvc.perform(get(base() + "/missions/current")).andExpect(status().isNotFound());
        long future = jdbc.queryForObject("select id from missions", Long.class);
        mvc.perform(post(base() + "/missions/" + future + "/start").contentType(MediaType.APPLICATION_JSON)
                .content(position())).andExpect(status().isConflict());
        mvc.perform(get(base() + "/missions/" + future + "/hint")).andExpect(status().isConflict());
        create(1);
        mvc.perform(get(base() + "/missions/current")).andExpect(status().isOk());
    }

    @Test
    void unsatisfiedConditionsRollBackWithoutLeavingAnEmptyCurriculum() throws Exception {
        jdbc.update("delete from preferences");
        mvc.perform(post(base() + "/adventure/process").contentType(MediaType.APPLICATION_JSON)
                .content(createBody(1))).andExpect(status().isConflict());
        assertEquals(0, jdbc.queryForObject("select count(*) from adventure_processes", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from missions", Integer.class));
    }

    @Test
    void repeatedStartPreservesTimestampAtDatabasePrecision() throws Exception {
        long mission = create(1).path("first_mission_id").asLong();
        when(settings.nowUtc()).thenReturn(START.plusNanos(123_456_789));
        String initial = start(mission).path("started_at").asString();
        when(settings.nowUtc()).thenReturn(START.plusMinutes(1));
        String repeated = start(mission).path("started_at").asString();
        assertEquals(initial, repeated);
        mvc.perform(get(base() + "/missions/current")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.started_at").value(initial));
    }

    @Test
    void clientCompletionTimeCannotImproveRankOrReplaceServerTimestamp() throws Exception {
        long mission = create(1).path("first_mission_id").asLong();
        start(mission);
        when(settings.nowUtc()).thenReturn(START.plusMinutes(10));
        mvc.perform(post(base() + "/missions/" + mission + "/complete").contentType(MediaType.APPLICATION_JSON)
                .content("{\"latitude\":35,\"longitude\":129,\"completed_at\":\"2026-10-19T01:01:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed_at").value("2026-10-19T01:10:00Z"));
        assertEquals("C", jdbc.queryForObject("select clear_rank from missions where id = ?", String.class, mission));
    }

    @Test
    void concurrentCreationAndHintsDoNotDuplicateOrLoseUpdates() throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            Callable<Integer> create = () -> {
                ready.countDown();
                assertTrue(go.await(10, TimeUnit.SECONDS));
                try {
                    service.create(groupId, new AdventureDtos.CreateRequest(DATE, 1));
                    return 201;
                } catch (ApiException ex) { return ex.getStatus().value(); }
            };
            Future<Integer> first = pool.submit(create);
            Future<Integer> second = pool.submit(create);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            assertEquals(List.of(201, 409), List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
                    .stream().sorted().toList());
            assertEquals(1, jdbc.queryForObject("select count(*) from adventure_processes", Integer.class));
            long mission = jdbc.queryForObject("select id from missions", Long.class);
            List<Callable<AdventureDtos.Hint>> hints = java.util.stream.IntStream.range(0, 12)
                    .<Callable<AdventureDtos.Hint>>mapToObj(index -> () -> service.hint(groupId, mission)).toList();
            for (Future<AdventureDtos.Hint> hint : pool.invokeAll(hints)) { hint.get(10, TimeUnit.SECONDS); }
            assertEquals(12, jdbc.queryForObject("select hint_count from missions where id = ?", Integer.class, mission));
            start(mission);
            List<Callable<Integer>> completions = List.of(() -> completeStatus(mission), () -> completeStatus(mission));
            List<Future<Integer>> results = pool.invokeAll(completions);
            assertEquals(List.of(200, 409), List.of(results.get(0).get(), results.get(1).get()).stream().sorted().toList());
        }
    }

    @Test
    void errorDispatchPreservesStatusInsteadOfBecomingForbidden() throws Exception {
        mvc.perform(get("/error").with(request -> { request.setDispatcherType(DispatcherType.ERROR); return request; })
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 405)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, base() + "/missions/current")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.status").value(405));
        mvc.perform(post("/error").with(request -> { request.setDispatcherType(DispatcherType.ERROR); return request; })
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, base() + "/adventure/process")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.status").value(500));
        mvc.perform(get("/error")).andExpect(status().isForbidden());
        mvc.perform(get("/unmatched")).andExpect(status().isForbidden());
    }

    private int completeStatus(long mission) {
        try {
            service.complete(groupId, mission, new AdventureDtos.CompleteRequest(35.0, 129.0, null));
            return 200;
        } catch (ApiException ex) { return ex.getStatus().value(); }
    }

    private long seedGroup() {
        return tx.execute(transaction -> {
            User user = User.builder().googleSub(java.util.UUID.randomUUID().toString()).userName("테스터").build();
            em.persist(user);
            Group group = Group.builder().owner(user).groupName("모험").memberCount(1).maxMembers(4).build();
            em.persist(group);
            em.persist(GroupMember.builder().group(group).user(user).build());
            Trip trip = Trip.builder().group(group).region("BUSAN").mode(TripMode.ADVENTURE)
                    .travelStartDate(DATE).travelEndDate(DATE.plusDays(1)).build();
            em.persist(trip);
            em.persist(Preference.builder().trip(trip).user(user).allergies("[]").budgetMax(60_000).build());
            for (int i = 0; i < 3; i++) {
                em.persist(Store.builder().destinationName("정답" + i).destinationType(Type.ART_SPORTS)
                        .sido("부산광역시").latitude(35.0).longitude(129.0).build());
            }
            em.flush();
            return group.getId();
        });
    }

    private String base() { return "/api/v1/groups/" + groupId; }
    private String position() { return "{\"latitude\":35,\"longitude\":129}"; }
    private String createBody(int count) { return "{\"travel_date\":\"2026-10-19\",\"mission_count\":" + count + "}"; }
    private JsonNode create(int count) throws Exception {
        return data(mvc.perform(post(base() + "/adventure/process").contentType(MediaType.APPLICATION_JSON)
                .content(createBody(count))).andExpect(status().isCreated()).andReturn());
    }
    private JsonNode start(long mission) throws Exception {
        return data(mvc.perform(post(base() + "/missions/" + mission + "/start").contentType(MediaType.APPLICATION_JSON)
                .content(position())).andExpect(status().isOk()).andReturn());
    }
    private JsonNode complete(long mission) throws Exception {
        return data(mvc.perform(post(base() + "/missions/" + mission + "/complete").contentType(MediaType.APPLICATION_JSON)
                .content(position())).andExpect(status().isOk()).andReturn());
    }
    private JsonNode data(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}
