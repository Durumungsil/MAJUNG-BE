package durumungsil.majung;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import durumungsil.majung.domain.group.entity.*;
import durumungsil.majung.domain.place.entity.GooglePlace;
import durumungsil.majung.domain.preference.entity.TravelCategory;
import durumungsil.majung.domain.preference.profile.ProfileWorker;
import durumungsil.majung.domain.roulette.entity.*;
import durumungsil.majung.domain.roulette.service.RouletteService;
import durumungsil.majung.domain.trip.entity.*;
import durumungsil.majung.domain.user.entity.User;
import durumungsil.majung.global.places.GooglePlacesClient;
import durumungsil.majung.global.security.jwt.JwtTokenProvider;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;

@SpringBootTest
class RouletteConfirmationApiTests {
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired JwtTokenProvider tokens;
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper mapper;
    @Autowired RouletteService service;
    @MockitoBean GooglePlacesClient client;
    @MockitoBean ProfileWorker worker;
    MockMvc mvc;

    record Fixture(long owner, long member, long outsider, long group, long trip) {
        String base() {
            return "/api/v2/groups/" + group + "/roulette";
        }
    }

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    Fixture seed(TripMode mode) {
        return tx.execute(
                status -> {
                    var owner = user();
                    var member = user();
                    var outsider = user();
                    var group =
                            Group.builder()
                                    .groupName("확정 테스트")
                                    .owner(owner)
                                    .memberCount(2)
                                    .maxMembers(4)
                                    .build();
                    em.persist(group);
                    em.persist(GroupMember.builder().group(group).user(owner).build());
                    em.persist(GroupMember.builder().group(group).user(member).build());
                    var trip = Trip.builder().group(group).region("부산").mode(mode).build();
                    em.persist(trip);
                    return new Fixture(
                            owner.getId(),
                            member.getId(),
                            outsider.getId(),
                            group.getId(),
                            trip.getId());
                });
    }

    User user() {
        var user = User.builder().googleSub(UUID.randomUUID().toString()).build();
        em.persist(user);
        return user;
    }

    long draw(Fixture f, RouletteType type, int round) {
        return tx.execute(
                status -> {
                    var place = new GooglePlace("confirmation-" + UUID.randomUUID());
                    em.persist(place);
                    var result =
                            RouletteResult.builder()
                                    .trip(em.find(Trip.class, f.trip()))
                                    .type(type)
                                    .googlePlace(place)
                                    .round(round)
                                    .category(
                                            type == RouletteType.RESTAURANT
                                                    ? TravelCategory.FOOD
                                                    : TravelCategory.CULTURE)
                                    .build();
                    em.persist(result);
                    return result.getId();
                });
    }

    String auth(long user) {
        return "Bearer " + tokens.createAccessToken(user);
    }

    JsonNode current(Fixture f, long user) throws Exception {
        return mapper.readTree(
                        mvc.perform(get(f.base() + "/current").header("Authorization", auth(user)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                .path("data");
    }

    JsonNode confirm(Fixture f, long result, long user) throws Exception {
        return mapper.readTree(
                        mvc.perform(
                                        put(f.base() + "/" + result + "/confirm")
                                                .header("Authorization", auth(user)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                .path("data");
    }

    @Test
    void membersRestoreIndependentConfirmedCardsAndRetriesDoNotReplaceTheLatest() throws Exception {
        var f = seed(TripMode.EXTREME);
        long destination = draw(f, RouletteType.DESTINATION, 1);
        long restaurant = draw(f, RouletteType.RESTAURANT, 1);
        var empty = current(f, f.member());
        assertThat(empty.path("group_id").asLong()).isEqualTo(f.group());
        assertThat(empty.path("trip_id").asLong()).isEqualTo(f.trip());
        assertThat(empty.has("destination") && empty.get("destination").isNull()).isTrue();
        assertThat(empty.has("restaurant") && empty.get("restaurant").isNull()).isTrue();

        var first = confirm(f, destination, f.member());
        assertThat(first.path("confirmed_at").asText()).isNotBlank();
        var restored = current(f, f.owner());
        assertThat(restored.path("destination")).isEqualTo(first);
        assertThat(restored.path("restaurant").isNull()).isTrue();
        mvc.perform(get(f.base() + "/" + destination).header("Authorization", auth(f.member())))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.confirmed_at").value(first.path("confirmed_at").asText()));

        long next = draw(f, RouletteType.DESTINATION, 2);
        assertThat(current(f, f.member()).path("destination")).isEqualTo(first);
        var second = confirm(f, next, f.owner());
        // 이전 결과의 늦은 재요청이 최신 확정을 되돌리지 않는다.
        assertThat(confirm(f, destination, f.member())).isEqualTo(first);
        var food = confirm(f, restaurant, f.member());
        var cards = current(f, f.member());
        assertThat(cards.path("destination")).isEqualTo(second);
        assertThat(cards.path("restaurant")).isEqualTo(food);
        verifyNoInteractions(client, worker);
    }

    @Test
    void latestUsesConfirmationOrderInsteadOfDrawOrder() throws Exception {
        var f = seed(TripMode.EXTREME);
        long olderDraw = draw(f, RouletteType.DESTINATION, 1);
        long newerDraw = draw(f, RouletteType.DESTINATION, 2);
        confirm(f, newerDraw, f.owner());
        var chosen = confirm(f, olderDraw, f.member());
        assertThat(current(f, f.owner()).path("destination")).isEqualTo(chosen);
    }

    @Test
    void nextTripStartsEmptyAndCannotConfirmPreviousTripResults() throws Exception {
        var f = seed(TripMode.EXTREME);
        long old = draw(f, RouletteType.DESTINATION, 1);
        confirm(f, old, f.owner());
        tx.executeWithoutResult(
                status ->
                        em.createQuery("update Trip t set t.status = :status where t.id = :id")
                                .setParameter("status", TripStatus.COMPLETED)
                                .setParameter("id", f.trip())
                                .executeUpdate());
        // 다른 조회 API와 같이 활성 회차가 없으면 마지막 완료 회차를 보여준다.
        assertThat(current(f, f.member()).path("destination").path("roulette_id").asLong())
                .isEqualTo(old);
        mvc.perform(put(f.base() + "/" + old + "/confirm").header("Authorization", auth(f.owner())))
                .andExpect(status().isConflict());
        long nextTrip =
                tx.execute(
                        status -> {
                            var trip =
                                    Trip.builder()
                                            .group(em.find(Group.class, f.group()))
                                            .mode(TripMode.EXTREME)
                                            .build();
                            em.persist(trip);
                            return trip.getId();
                        });
        var empty = current(f, f.member());
        assertThat(empty.path("trip_id").asLong()).isEqualTo(nextTrip);
        assertThat(empty.path("destination").isNull()).isTrue();
        assertThat(empty.path("restaurant").isNull()).isTrue();
        mvc.perform(put(f.base() + "/" + old + "/confirm").header("Authorization", auth(f.owner())))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingAuthenticationMembershipAndForeignResultsAreRejected() throws Exception {
        var f = seed(TripMode.EXTREME);
        long result = draw(f, RouletteType.DESTINATION, 1);
        var other = seed(TripMode.EXTREME);
        long foreign = draw(other, RouletteType.DESTINATION, 1);
        mvc.perform(get(f.base() + "/current")).andExpect(status().isUnauthorized());
        mvc.perform(put(f.base() + "/" + result + "/confirm")).andExpect(status().isUnauthorized());
        mvc.perform(get(f.base() + "/current").header("Authorization", auth(f.outsider())))
                .andExpect(status().isNotFound());
        mvc.perform(
                        put(f.base() + "/" + result + "/confirm")
                                .header("Authorization", auth(f.outsider())))
                .andExpect(status().isNotFound());
        mvc.perform(
                        put(f.base() + "/" + foreign + "/confirm")
                                .header("Authorization", auth(f.owner())))
                .andExpect(status().isNotFound());
        mvc.perform(
                        put(f.base() + "/9223372036854775807/confirm")
                                .header("Authorization", auth(f.owner())))
                .andExpect(status().isNotFound());
        assertThat(current(f, f.member()).path("destination").isNull()).isTrue();
        verifyNoInteractions(client, worker);
    }

    @Test
    void wrongModeAndMissingTripAreConflicts() throws Exception {
        var f = seed(TripMode.STORY);
        long result = draw(f, RouletteType.DESTINATION, 1);
        mvc.perform(get(f.base() + "/current").header("Authorization", auth(f.owner())))
                .andExpect(status().isConflict());
        mvc.perform(
                        put(f.base() + "/" + result + "/confirm")
                                .header("Authorization", auth(f.owner())))
                .andExpect(status().isConflict());
        var noTrip = seed(TripMode.EXTREME);
        tx.executeWithoutResult(status -> em.remove(em.find(Trip.class, noTrip.trip())));
        mvc.perform(get(noTrip.base() + "/current").header("Authorization", auth(noTrip.owner())))
                .andExpect(status().isConflict());
        mvc.perform(
                        put(noTrip.base() + "/" + result + "/confirm")
                                .header("Authorization", auth(noTrip.owner())))
                .andExpect(status().isConflict());
        verifyNoInteractions(client, worker);
    }

    @Test
    void concurrentConfirmationsOfTheSameResultKeepOneTimestamp() throws Exception {
        var f = seed(TripMode.EXTREME);
        long result = draw(f, RouletteType.DESTINATION, 1);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Instant> owner =
                    () -> {
                        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                        return service.confirm(f.owner(), f.group(), result).confirmedAt();
                    };
            Callable<Instant> member =
                    () -> {
                        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                        return service.confirm(f.member(), f.group(), result).confirmedAt();
                    };
            var first = pool.submit(owner);
            var second = pool.submit(member);
            start.countDown();
            Instant confirmedAt = first.get(10, TimeUnit.SECONDS);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(confirmedAt);
            assertThat(service.get(f.owner(), f.group(), result).confirmedAt())
                    .isEqualTo(confirmedAt);
        }
    }
}
