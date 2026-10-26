package durumungsil.majung;

import durumungsil.majung.domain.group.entity.Group;
import durumungsil.majung.domain.group.entity.GroupMember;
import durumungsil.majung.domain.preference.entity.Preference;
import durumungsil.majung.domain.roulette.dto.request.DrawRouletteRequest;
import durumungsil.majung.domain.roulette.dto.response.DrawRouletteResponse;
import durumungsil.majung.domain.roulette.entity.RouletteResult;
import durumungsil.majung.domain.roulette.entity.RouletteType;
import durumungsil.majung.domain.roulette.service.RouletteService;
import durumungsil.majung.domain.store.entity.Store;
import durumungsil.majung.domain.store.entity.Type;
import durumungsil.majung.domain.trip.entity.Trip;
import durumungsil.majung.domain.trip.entity.TripMode;
import durumungsil.majung.domain.trip.entity.TripStatus;
import durumungsil.majung.domain.user.entity.User;
import durumungsil.majung.global.security.jwt.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class ExtremeModeApiTests {
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired JwtTokenProvider tokens;
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper mapper;
    @Autowired RouletteService service;
    MockMvc mvc;

    record Fixture(long owner, long member, long outsider, long departed, long group, long trip, String region,
                   long first, long second, long unsafe, long korean, long otherKorean, long rawFood, long shop) {
        String base() { return "/api/v2/groups/" + group; }
    }

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    User user() {
        User user = User.builder().googleSub(UUID.randomUUID().toString()).build();
        em.persist(user);
        return user;
    }

    Store store(String region, Type type, String category, Set<String> hazards) {
        Store store = Store.builder().destinationName("테스트 장소").destinationType(type)
                .sido(region).address("테스트 주소").latitude(35.1).longitude(129.1)
                .placeCategory(category).safetyCategories(hazards).build();
        em.persist(store);
        return store;
    }

    Fixture seed(TripMode mode, boolean withPreferences) {
        return tx.execute(status -> {
            User owner = user(), member = user(), outsider = user(), departed = user();
            Group group = Group.builder().groupName("익스트림").owner(owner).memberCount(2).maxMembers(4).build();
            em.persist(group);
            em.persist(GroupMember.builder().group(group).user(owner).build());
            em.persist(GroupMember.builder().group(group).user(member).build());
            String region = "EXTREME-" + UUID.randomUUID();
            Trip previous = Trip.builder().group(group).region(region).mode(TripMode.EXTREME)
                    .status(TripStatus.COMPLETED).build();
            em.persist(previous);
            em.persist(Preference.builder().trip(previous).user(owner).allergies("[\"OLD\"]")
                    .excludedCategories("[\"CULTURE\"]").build());
            Trip trip = Trip.builder().group(group).region(region).mode(mode)
                    .travelStartDate(LocalDate.now()).travelEndDate(LocalDate.now()).build();
            em.persist(trip);
            if (withPreferences) {
                em.persist(Preference.builder().trip(trip).user(owner).allergies("[\"PEANUT\"]")
                        .excludedCategories("[\"EXTREME_HEIGHT\"]").build());
                em.persist(Preference.builder().trip(trip).user(member).allergies("[\"MILK\",\"PEANUT\"]")
                        .excludedCategories("[\"NATURE\",\"EXTREME_HEIGHT\"]").build());
            }
            // 탈퇴자의 취향은 어느 경우에도 최소 조건에 포함하지 않는다.
            em.persist(Preference.builder().trip(trip).user(departed).allergies("[\"DEPARTED\"]")
                    .excludedCategories("[\"CULTURE\"]").build());
            Store first = store(region, Type.ART_SPORTS, null, Set.of());
            Store second = store(region, Type.ART_SPORTS, "CULTURE", Set.of());
            Store unsafe = store(region, Type.ART_SPORTS, "CULTURE", Set.of("EXTREME_HEIGHT"));
            Store korean = store(region, Type.FOOD, "KOREAN", Set.of());
            Store otherKorean = store(region, Type.FOOD, "KOREAN", Set.of());
            Store rawFood = store(region, Type.FOOD, null, Set.of());
            Store shop = store(region, Type.RETAIL, null, Set.of());
            store(region, Type.FOOD, "JAPANESE", Set.of());
            store("OTHER-" + region, Type.ART_SPORTS, "CULTURE", Set.of());
            store(region, Type.LODGING, null, Set.of());
            // 같은 장소의 이전 회차 결과가 현재 회차 추첨을 막아서는 안 된다.
            em.persist(RouletteResult.builder().trip(previous).store(first).type(RouletteType.DESTINATION).round(1).build());
            return new Fixture(owner.getId(), member.getId(), outsider.getId(), departed.getId(), group.getId(),
                    trip.getId(), region, first.getDestinationId(), second.getDestinationId(), unsafe.getDestinationId(),
                    korean.getDestinationId(), otherKorean.getDestinationId(), rawFood.getDestinationId(), shop.getDestinationId());
        });
    }

    String bearer(long user) { return "Bearer " + tokens.createAccessToken(user); }

    ResultActions draw(Fixture f, String type, String json) throws Exception {
        return mvc.perform(post(f.base() + "/roulette/" + type).header("Authorization", bearer(f.owner()))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    JsonNode data(ResultActions action) throws Exception {
        return mapper.readTree(action.andReturn().getResponse().getContentAsString()).get("data");
    }

    @Test
    void minimumConditionsUnionOnlyCurrentMembersOfCurrentTripAndRefreshAfterEdits() throws Exception {
        Fixture f = seed(TripMode.EXTREME, true);
        mvc.perform(get(f.base() + "/constraints/minimum").header("Authorization", bearer(f.member())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.source_member_count").value(2))
                .andExpect(jsonPath("$.data.allergies").value(org.hamcrest.Matchers.contains("MILK", "PEANUT")))
                .andExpect(jsonPath("$.data.excluded_categories").value(org.hamcrest.Matchers.contains("EXTREME_HEIGHT", "NATURE")));
        mvc.perform(put("/api/v1/groups/" + f.group() + "/preferences").header("Authorization", bearer(f.owner()))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"transport_types":["WALK"],"food_preferences":[],"allergies":["EGG"],
                        "travel_categories":["CULTURE"],"excluded_categories":["SHOPPING","SHOPPING"]}
                        """))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/groups/" + f.group() + "/preferences/me").header("Authorization", bearer(f.owner())))
                .andExpect(jsonPath("$.data.excluded_categories").value(org.hamcrest.Matchers.contains("SHOPPING")));
        JsonNode updated = data(mvc.perform(get(f.base() + "/constraints/minimum")
                .header("Authorization", bearer(f.owner()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allergies").value(org.hamcrest.Matchers.contains("EGG", "MILK", "PEANUT")))
                .andExpect(jsonPath("$.data.excluded_categories").value(org.hamcrest.Matchers.contains("EXTREME_HEIGHT", "NATURE", "SHOPPING"))));
        assertNotNull(Instant.parse(updated.get("updated_at").asString()));
        tx.executeWithoutResult(status -> em.createQuery("delete from GroupMember m where m.group.id = :g and m.user.id = :u")
                .setParameter("g", f.group()).setParameter("u", f.member()).executeUpdate());
        mvc.perform(get(f.base() + "/constraints/minimum").header("Authorization", bearer(f.owner())))
                .andExpect(jsonPath("$.data.source_member_count").value(1))
                .andExpect(jsonPath("$.data.allergies").value(org.hamcrest.Matchers.contains("EGG")))
                .andExpect(jsonPath("$.data.excluded_categories").value(org.hamcrest.Matchers.contains("SHOPPING")));
        draw(f, "destinations", "{\"category\":\"SHOPPING\"}").andExpect(status().isConflict());
    }

    @Test
    void destinationsExcludeHazardsExplicitIdsAndHistoryAndPersistCoordinates() throws Exception {
        Fixture f = seed(TripMode.EXTREME, true);
        JsonNode first = data(draw(f, "destinations", "{\"category\":\"CULTURE\",\"exclude_place_ids\":[" + f.second() + "]}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.round").value(1))
                .andExpect(jsonPath("$.data.place.place_id").value(f.first()))
                .andExpect(jsonPath("$.data.group_allergies").doesNotExist()));
        Instant.parse(first.get("drawn_at").asString());
        long id = first.get("roulette_id").asLong();
        JsonNode loaded = data(mvc.perform(get(f.base() + "/roulette/" + id)
                        .header("Authorization", bearer(f.member())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.group_id").value(f.group())));
        assertEquals(first.get("place"), loaded.get("place"));
        assertEquals(first.get("drawn_at"), loaded.get("drawn_at"));
        draw(f, "destinations", "{\"category\":\"CULTURE\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.round").value(2)).andExpect(jsonPath("$.data.place.place_id").value(f.second()));
        draw(f, "destinations", "{\"category\":\"CULTURE\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("조건에 맞는 여행지가 없어 추첨할 수 없습니다."));
        // 실패 요청은 라운드를 소비하지 않는다.
        draw(f, "destinations", "{\"category\":\"SHOPPING\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.round").value(3));
    }

    @Test
    void restaurantsFilterExactCategoryAndReturnAllergiesWithoutExcludingCandidates() throws Exception {
        Fixture f = seed(TripMode.EXTREME, true);
        draw(f, "restaurants", "{\"category\":\"KOREAN\",\"exclude_place_ids\":[" + f.otherKorean() + "]}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.place.place_id").value(f.korean()))
                .andExpect(jsonPath("$.data.place.category").value("KOREAN"))
                .andExpect(jsonPath("$.data.group_allergies").value(org.hamcrest.Matchers.contains("MILK", "PEANUT")))
                .andExpect(jsonPath("$.data.round").value(1));
        draw(f, "restaurants", "{\"category\":\"KOREAN\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.place.place_id").value(f.otherKorean()));
        draw(f, "restaurants", "{\"category\":\"KOREAN\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("조건에 맞는 음식점이 없어 추첨할 수 없습니다."));
        // 미분류 식당은 KOREAN으로 추정하지 않고 FOOD 요청에서만 선택한다.
        draw(f, "restaurants", "{\"category\":\"FOOD\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.place.place_id").value(f.rawFood()));
        draw(f, "destinations", "{\"category\":\"SHOPPING\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.round").value(1));
    }

    @Test
    void authenticationMembershipAndResultGroupAreChecked() throws Exception {
        Fixture f = seed(TripMode.EXTREME, true);
        mvc.perform(post(f.base() + "/roulette/destinations").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(f.base() + "/constraints/minimum")).andExpect(status().isUnauthorized());
        for (String path : List.of("/constraints/minimum", "/roulette/999999")) {
            mvc.perform(get(f.base() + path).header("Authorization", bearer(f.outsider()))).andExpect(status().isNotFound());
        }
        mvc.perform(post(f.base() + "/roulette/restaurants").header("Authorization", bearer(f.outsider()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        long id = data(draw(f, "destinations", "{}" ).andExpect(status().isCreated())).get("roulette_id").asLong();
        Fixture other = seed(TripMode.EXTREME, true);
        mvc.perform(get(other.base() + "/roulette/" + id).header("Authorization", bearer(other.owner())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("룰렛 결과를 찾을 수 없습니다."));
        mvc.perform(get(f.base() + "/roulette/" + Long.MAX_VALUE).header("Authorization", bearer(f.owner())))
                .andExpect(status().isNotFound());
    }

    @Test
    void wrongModeMissingPreferencesAndCompletedTripsDoNotDraw() throws Exception {
        Fixture wrong = seed(TripMode.STORY, true);
        draw(wrong, "destinations", "{}").andExpect(status().isConflict());
        Fixture missing = seed(TripMode.EXTREME, false);
        mvc.perform(get(missing.base() + "/constraints/minimum").header("Authorization", bearer(missing.owner())))
                .andExpect(status().isNotFound());
        draw(missing, "restaurants", "{}").andExpect(status().isNotFound());
        Fixture f = seed(TripMode.EXTREME, true);
        long id = data(draw(f, "restaurants", "{}").andExpect(status().isCreated())).get("roulette_id").asLong();
        tx.executeWithoutResult(status -> em.createQuery("update Trip t set t.status = :s where t.id = :id")
                .setParameter("s", TripStatus.COMPLETED).setParameter("id", f.trip()).executeUpdate());
        draw(f, "destinations", "{}").andExpect(status().isConflict());
        mvc.perform(get(f.base() + "/roulette/" + id).header("Authorization", bearer(f.owner())))
                .andExpect(status().isOk());
    }

    @Test
    void validatesRegionCategoryAndExclusionIds() throws Exception {
        Fixture f = seed(TripMode.EXTREME, true);
        for (String json : List.of("{\"region\":\"SEOUL\"}", "{\"region\":\" \"}",
                "{\"category\":\"culture\"}", "{\"category\":\"\"}",
                "{\"exclude_place_ids\":[0]}", "{\"exclude_place_ids\":[null]}", "{\"exclude_place_ids\":[-1]}")) {
            draw(f, "destinations", json).andExpect(status().isBadRequest());
        }
        draw(f, "restaurants", "{\"category\":\"UNAVAILABLE_CATEGORY\"}").andExpect(status().isConflict());
        draw(f, "destinations", "{\"region\":\"" + f.region() + "\",\"category\":\"CULTURE\"}")
                .andExpect(status().isCreated());
    }

    @Test
    void concurrentDrawsHaveDistinctRoundsAndPlaces() throws Exception {
        Fixture f = seed(TripMode.EXTREME, true);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(i -> pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("추첨 시작 대기 시간 초과");
                return service.draw(f.owner(), f.group(), RouletteType.DESTINATION,
                        new DrawRouletteRequest(null, "CULTURE", List.of()));
            })).toList();
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            DrawRouletteResponse a = tasks.get(0).get(15, TimeUnit.SECONDS);
            DrawRouletteResponse b = tasks.get(1).get(15, TimeUnit.SECONDS);
            assertEquals(Set.of(1, 2), Set.of(a.round(), b.round()));
            assertEquals(Set.of(f.first(), f.second()), Set.of(a.place().placeId(), b.place().placeId()));
        }
        draw(f, "destinations", "{\"category\":\"CULTURE\"}").andExpect(status().isConflict());
    }
}
