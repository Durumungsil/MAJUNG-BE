package durumungsil.majung;

import durumungsil.majung.domain.group.entity.Group;
import durumungsil.majung.domain.group.entity.GroupMember;
import durumungsil.majung.domain.preference.entity.Preference;
import durumungsil.majung.domain.preference.entity.TransportType;
import durumungsil.majung.domain.store.entity.Store;
import durumungsil.majung.domain.store.entity.Type;
import durumungsil.majung.domain.trip.entity.Trip;
import durumungsil.majung.domain.trip.entity.TripMode;
import durumungsil.majung.domain.user.entity.User;
import durumungsil.majung.global.security.jwt.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 스토리모드 API 전체 흐름: 추천 → 2차 추천 → 선택 저장 → 타임라인 생성 → 조회 → 항목 수정 */
@SpringBootTest
class StoryModeApiTests {
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired JwtTokenProvider tokens;
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper mapper;

    static final AtomicInteger SEQUENCE = new AtomicInteger();

    MockMvc mvc;

    /** 다른 테스트의 장소와 섞이지 않도록 테스트마다 지역 이름과 좌표를 따로 쓴다 */
    record Fixture(long owner, long outsider, long group, long culture, long cultureNearby, long shop, long far,
                   String region, LocalDate start) {
        String base() {
            return "/api/v1/groups/" + group;
        }
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    Fixture seed(TripMode mode, boolean withPreference) {
        String region = "TEST-" + UUID.randomUUID();
        LocalDate start = LocalDate.now();
        // 바다 위 좌표를 픽스처마다 약 11km씩 떨어뜨려 2차 추천 반경에 다른 픽스처 장소가 섞이지 않게 한다
        double latitude = 33.0 + SEQUENCE.getAndIncrement() * 0.1;
        double longitude = 124.0;
        return tx.execute(status -> {
            User owner = User.builder().googleSub(UUID.randomUUID().toString()).build();
            User outsider = User.builder().googleSub(UUID.randomUUID().toString()).build();
            em.persist(owner);
            em.persist(outsider);
            Group group = Group.builder().groupName("스토리").owner(owner).memberCount(1).maxMembers(4).build();
            em.persist(group);
            em.persist(GroupMember.builder().group(group).user(owner).build());
            Trip trip = Trip.builder().group(group).region(region).mode(mode)
                    .travelStartDate(start).travelEndDate(start.plusDays(1)).build();
            em.persist(trip);
            if (withPreference) {
                em.persist(Preference.builder().trip(trip).user(owner).transportTypes(Set.of(TransportType.WALK))
                        .travelCategories("[\"CULTURE\"]").allergies("[]").budgetMax(300000).build());
            }
            Store culture = Store.builder().destinationName("문화 공간").destinationType(Type.ART_SPORTS)
                    .sido(region).latitude(latitude).longitude(longitude).rating(4.0).build();
            Store cultureNearby = Store.builder().destinationName("근처 전시관").destinationType(Type.ART_SPORTS)
                    .sido(region).latitude(latitude + 0.008).longitude(longitude).rating(4.5).build();
            Store shop = Store.builder().destinationName("기념품 가게").destinationType(Type.RETAIL)
                    .sido(region).latitude(latitude + 0.004).longitude(longitude).rating(5.0).build();
            Store hotel = Store.builder().destinationName("호텔").destinationType(Type.LODGING)
                    .sido(region).latitude(latitude).longitude(longitude + 0.001).build();
            // 약 5.6km 북쪽: 도보 범위(3km) 밖, 버스 범위(15km) 안
            Store far = Store.builder().destinationName("먼 박물관").destinationType(Type.ART_SPORTS)
                    .sido(region).latitude(latitude + 0.05).longitude(longitude).rating(3.0).build();
            em.persist(culture);
            em.persist(cultureNearby);
            em.persist(shop);
            em.persist(hotel);
            em.persist(far);
            return new Fixture(owner.getId(), outsider.getId(), group.getId(), culture.getDestinationId(),
                    cultureNearby.getDestinationId(), shop.getDestinationId(), far.getDestinationId(), region, start);
        });
    }

    String bearer(long userId) {
        return "Bearer " + tokens.createAccessToken(userId);
    }

    @Test
    void storyModeFlowFromRecommendationToTimelineEdit() throws Exception {
        Fixture data = seed(TripMode.STORY, true);
        String owner = bearer(data.owner());

        // 여행지 리스트 추천: 문화 선호 → 문화 장소가 먼저, 숙박은 제외
        // 선택 전에는 취향 1위(근처 전시관, 평점 4.5)가 중심이고, 도보 범위 3km 밖의 먼 박물관은 빠진다
        // 점수: 0.4 + 0.4 × 선호 비율 + 0.2 × (1 - 거리/3000). 문화 공간은 약 890m → 0.94
        mvc.perform(get(data.base() + "/destinations/recommendations").header("Authorization", owner)
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.region").value(data.region()))
                .andExpect(jsonPath("$.data.area.based_on").value("TOP_RECOMMENDATION"))
                .andExpect(jsonPath("$.data.area.transport_type").value("WALK"))
                .andExpect(jsonPath("$.data.area.radius_meters").value(3000))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].destination_id").value(data.cultureNearby()))
                .andExpect(jsonPath("$.data.items[0].category").value("CULTURE"))
                .andExpect(jsonPath("$.data.items[0].distance_meters").value(0))
                .andExpect(jsonPath("$.data.items[0].score").value(1.0))
                .andExpect(jsonPath("$.data.items[0].reason").value("문화 선호와 일치"))
                .andExpect(jsonPath("$.data.items[1].destination_id").value(data.culture()))
                .andExpect(jsonPath("$.data.items[1].score").value(0.94))
                .andExpect(jsonPath("$.data.next_cursor").value("2"));
        mvc.perform(get(data.base() + "/destinations/recommendations").header("Authorization", owner)
                        .param("limit", "2").param("cursor", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].category").value("SHOPPING"))
                .andExpect(jsonPath("$.data.next_cursor").doesNotExist());

        // 2차 추천: 기준 장소·숙박 제외. 근처 전시관(약 890m, 문화 선호) 0.88 > 기념품 가게(약 445m) 0.74
        mvc.perform(get(data.base() + "/destinations/" + data.culture() + "/recommendations")
                        .header("Authorization", owner).param("radius_meters", "3000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.base_destination.destination_id").value(data.culture()))
                .andExpect(jsonPath("$.data.radius_meters").value(3000))
                .andExpect(jsonPath("$.data.transport_type").value("WALK"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].destination_id").value(data.cultureNearby()))
                .andExpect(jsonPath("$.data.items[0].score").value(0.88))
                .andExpect(jsonPath("$.data.items[1].destination_id").value(data.shop()))
                .andExpect(jsonPath("$.data.items[1].score").value(0.74))
                .andExpect(jsonPath("$.data.items[0].estimated_travel_minutes").isNumber());
        // 반경을 안 보내면 도보 기본값 1500m (적정 이동 2km ÷ 1.3)
        mvc.perform(get(data.base() + "/destinations/" + data.culture() + "/recommendations")
                        .header("Authorization", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.radius_meters").value(1500))
                .andExpect(jsonPath("$.data.items.length()").value(2));

        // 여행지 선택 저장
        String selection = """
                {"destinations":[
                  {"destination_id":%d,"order":1,"visit_date":"%s"},
                  {"destination_id":%d,"order":2,"visit_date":"%s"}]}
                """.formatted(data.culture(), data.start(), data.cultureNearby(), data.start());
        mvc.perform(put(data.base() + "/destinations/selection").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content(selection))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.destination_count").value(2));
        // 선택을 저장하면 선택한 장소 중 가운데 장소(거리 합이 같으면 앞 순서인 문화 공간)가 중심이 된다
        mvc.perform(get(data.base() + "/destinations/recommendations").header("Authorization", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.area.based_on").value("SELECTION"))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].destination_id").value(data.culture()))
                .andExpect(jsonPath("$.data.items[0].distance_meters").value(0));
        // 멀리 떨어진 장소가 섞여도 중심은 가까운 장소들이 모인 쪽에 잡힌다 (평균 좌표가 아님)
        String scattered = """
                {"destinations":[
                  {"destination_id":%d,"order":1,"visit_date":"%s"},
                  {"destination_id":%d,"order":2,"visit_date":"%s"},
                  {"destination_id":%d,"order":3,"visit_date":"%s"}]}
                """.formatted(data.far(), data.start(), data.culture(), data.start(),
                data.cultureNearby(), data.start());
        mvc.perform(put(data.base() + "/destinations/selection").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content(scattered))
                .andExpect(status().isOk());
        mvc.perform(get(data.base() + "/destinations/recommendations").header("Authorization", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].destination_id").value(data.cultureNearby()))
                .andExpect(jsonPath("$.data.items[0].distance_meters").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(3));
        mvc.perform(put(data.base() + "/destinations/selection").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content(selection))
                .andExpect(status().isOk());

        // 타임라인 생성 (destination_ids 없음 → 저장한 선택 사용)
        String created = mvc.perform(post(data.base() + "/itineraries/timeline").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transport_type\":\"WALK\",\"day_start_time\":\"09:00\",\"day_end_time\":\"21:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.generated_by").value("RULE")) // 테스트는 Gemini 키 없음
                .andExpect(jsonPath("$.data.max_move_meters").value(2000))
                .andExpect(jsonPath("$.data.days[0].items.length()").value(3))
                .andExpect(jsonPath("$.data.days[0].items[0].type").value("VISIT"))
                .andExpect(jsonPath("$.data.days[0].items[0].start_at").value("09:00"))
                .andExpect(jsonPath("$.data.days[0].items[0].end_at").value("10:30"))
                .andExpect(jsonPath("$.data.days[0].items[1].type").value("MOVE"))
                .andExpect(jsonPath("$.data.days[0].items[1].from_destination_id").value(data.culture()))
                .andExpect(jsonPath("$.data.days[0].items[1].to_destination_id").value(data.cultureNearby()))
                .andExpect(jsonPath("$.data.days[0].items[1].long_distance").value(false))
                .andExpect(jsonPath("$.data.days[0].items[0].long_distance").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        JsonNode timeline = mapper.readTree(created).path("data");
        long itineraryId = timeline.path("itinerary_id").asLong();
        long firstItem = timeline.path("days").path(0).path("items").path(0).path("item_id").asLong();
        long moveItem = timeline.path("days").path(0).path("items").path(1).path("item_id").asLong();

        // 조회
        mvc.perform(get(data.base() + "/itineraries/" + itineraryId).header("Authorization", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itinerary_id").value(itineraryId))
                .andExpect(jsonPath("$.data.generated_by").doesNotExist())
                .andExpect(jsonPath("$.data.days[0].items[0].destination_name").value("문화 공간"))
                .andExpect(jsonPath("$.data.days[0].items[0].memo").isEmpty());

        // 항목 수정
        String itemPath = data.base() + "/itineraries/" + itineraryId + "/items/";
        mvc.perform(patch(itemPath + firstItem).header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"start_at\":\"09:30\",\"end_at\":\"10:00\",\"memo\":\"사진 촬영\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.start_at").value("09:30"))
                .andExpect(jsonPath("$.data.memo").value("사진 촬영"))
                .andExpect(jsonPath("$.data.has_conflict").value(false));
        // 다음 방문과 겹치면 400
        mvc.perform(patch(itemPath + firstItem).header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"end_at\":\"13:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("400"));
        // 하루 일정 시간을 벗어나면 400
        mvc.perform(patch(itemPath + firstItem).header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"start_at\":\"08:00\"}"))
                .andExpect(status().isBadRequest());
        // 이동 항목은 수정 불가
        mvc.perform(patch(itemPath + moveItem).header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"memo\":\"x\"}"))
                .andExpect(status().isBadRequest());

        // 타임라인을 다시 만들면 이전 타임라인은 사라진다
        mvc.perform(post(data.base() + "/itineraries/timeline").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destination_ids\":[" + data.shop() + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.transport_type").value("WALK"))
                .andExpect(jsonPath("$.data.days[0].items.length()").value(1));
        mvc.perform(get(data.base() + "/itineraries/" + itineraryId).header("Authorization", owner))
                .andExpect(status().isNotFound());
    }

    @Test
    void timelineOrdersStopsByDistanceAndMovesFarPlaceToAnotherDay() throws Exception {
        Fixture data = seed(TripMode.STORY, true);
        String owner = bearer(data.owner());
        // 받은 순서: 문화 공간 → 먼 박물관(5.6km) → 근처 전시관(890m). 도보 적정 거리 2km
        // → 문화 공간 → 근처 전시관을 첫날에, 먼 박물관은 다음 날로
        mvc.perform(post(data.base() + "/itineraries/timeline").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transport_type\":\"WALK\",\"destination_ids\":[%d,%d,%d]}"
                                .formatted(data.culture(), data.far(), data.cultureNearby())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.days.length()").value(2))
                .andExpect(jsonPath("$.data.days[0].items.length()").value(3))
                .andExpect(jsonPath("$.data.days[0].items[0].destination_id").value(data.culture()))
                .andExpect(jsonPath("$.data.days[0].items[2].destination_id").value(data.cultureNearby()))
                .andExpect(jsonPath("$.data.days[1].items.length()").value(1))
                .andExpect(jsonPath("$.data.days[1].items[0].destination_id").value(data.far()));

        // 자동차면 같은 날 이어서 간다 (5.6km × 1.3 < 20km)
        mvc.perform(post(data.base() + "/itineraries/timeline").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transport_type\":\"CAR\",\"destination_ids\":[%d,%d,%d]}"
                                .formatted(data.culture(), data.far(), data.cultureNearby())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.max_move_meters").value(20000))
                .andExpect(jsonPath("$.data.days.length()").value(1))
                .andExpect(jsonPath("$.data.days[0].items[4].destination_id").value(data.far()))
                .andExpect(jsonPath("$.data.days[0].items[3].long_distance").value(false));

        // 하루짜리 일정이면 나눌 날이 없으니 같은 날에 두고 long_distance 로 알려준다
        mvc.perform(post(data.base() + "/itineraries/timeline").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transport_type\":\"WALK\",\"end_date\":\"%s\",\"destination_ids\":[%d,%d,%d]}"
                                .formatted(data.start(), data.culture(), data.far(), data.cultureNearby())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.days.length()").value(1))
                .andExpect(jsonPath("$.data.days[0].items[3].type").value("MOVE"))
                .andExpect(jsonPath("$.data.days[0].items[3].long_distance").value(true));
    }

    @Test
    void invalidSelectionIsRejected() throws Exception {
        Fixture data = seed(TripMode.STORY, true);
        String owner = bearer(data.owner());
        String duplicatedOrder = """
                {"destinations":[
                  {"destination_id":%d,"order":1,"visit_date":"%s"},
                  {"destination_id":%d,"order":1,"visit_date":"%s"}]}
                """.formatted(data.culture(), data.start(), data.shop(), data.start());
        mvc.perform(put(data.base() + "/destinations/selection").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content(duplicatedOrder))
                .andExpect(status().isBadRequest());
        String outOfPeriod = """
                {"destinations":[{"destination_id":%d,"order":1,"visit_date":"%s"}]}
                """.formatted(data.culture(), data.start().plusDays(10));
        mvc.perform(put(data.base() + "/destinations/selection").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content(outOfPeriod))
                .andExpect(status().isBadRequest());
        String unknownPlace = """
                {"destinations":[{"destination_id":%d,"order":1,"visit_date":"%s"}]}
                """.formatted(Long.MAX_VALUE, data.start());
        mvc.perform(put(data.base() + "/destinations/selection").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content(unknownPlace))
                .andExpect(status().isBadRequest());
        // 저장한 선택이 없으면 타임라인을 만들 수 없다
        mvc.perform(post(data.base() + "/itineraries/timeline").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recommendationRulesAndAccess() throws Exception {
        Fixture noPreference = seed(TripMode.STORY, false);
        String owner = bearer(noPreference.owner());
        // 취향이 없으면 카테고리를 지정하지 않은 추천은 409, 지정하면 동작
        mvc.perform(get(noPreference.base() + "/destinations/recommendations").header("Authorization", owner))
                .andExpect(status().isConflict());
        mvc.perform(get(noPreference.base() + "/destinations/recommendations").header("Authorization", owner)
                        .param("categories", "SHOPPING,CULTURE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.area.transport_type").value("BUS")) // 교통수단 취향이 없으면 버스
                .andExpect(jsonPath("$.data.area.radius_meters").value(15000))
                .andExpect(jsonPath("$.data.items.length()").value(4)); // 버스 범위면 먼 박물관도 포함
        // 잘못된 조건은 400
        mvc.perform(get(noPreference.base() + "/destinations/recommendations").header("Authorization", owner)
                        .param("categories", "UNKNOWN"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(noPreference.base() + "/destinations/" + noPreference.culture() + "/recommendations")
                        .header("Authorization", owner).param("radius_meters", "10"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(noPreference.base() + "/destinations/" + Long.MAX_VALUE + "/recommendations")
                        .header("Authorization", owner))
                .andExpect(status().isNotFound());

        // 그룹 멤버가 아니면 404, 토큰이 없으면 401
        mvc.perform(get(noPreference.base() + "/destinations/recommendations")
                        .header("Authorization", bearer(noPreference.outsider())).param("categories", "CULTURE"))
                .andExpect(status().isNotFound());
        mvc.perform(get(noPreference.base() + "/destinations/recommendations").param("categories", "CULTURE"))
                .andExpect(status().isUnauthorized());

        // 스토리모드가 아닌 여행은 409
        Fixture adventure = seed(TripMode.ADVENTURE, true);
        mvc.perform(get(adventure.base() + "/destinations/recommendations")
                        .header("Authorization", bearer(adventure.owner())))
                .andExpect(status().isConflict());
    }
}
