package durumungsil.majung;

import durumungsil.majung.domain.group.entity.*;
import durumungsil.majung.domain.preference.entity.*;
import durumungsil.majung.domain.preference.profile.ProfileWorker;
import durumungsil.majung.domain.place.repository.GooglePlaceRepository;
import durumungsil.majung.domain.trip.entity.*;
import durumungsil.majung.domain.user.entity.User;
import durumungsil.majung.global.places.GooglePlacesClient;
import durumungsil.majung.global.places.GooglePlacesClient.*;
import durumungsil.majung.global.security.jwt.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class StoryModeApiTests {
    @Autowired EntityManager em; @Autowired TransactionTemplate tx;
    @Autowired JwtTokenProvider tokens; @Autowired WebApplicationContext context;
    @Autowired ObjectMapper mapper; @Autowired GooglePlaceRepository places; @Autowired JdbcTemplate jdbc;
    @MockitoBean GooglePlacesClient client; @MockitoBean ProfileWorker worker;
    MockMvc mvc;
    record Fixture(long owner,long outsider,long group,long trip,String prefix,LocalDate date) {
        String base() { return "/api/v1/groups/"+group; }
        String id(int number) { return prefix+number; }
    }
    @BeforeEach void setUp() { mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    Fixture seed(boolean opinion,TripMode mode) {
        return tx.execute(status->{
            var owner=User.builder().googleSub(UUID.randomUUID().toString()).build(); em.persist(owner);
            var outsider=User.builder().googleSub(UUID.randomUUID().toString()).build(); em.persist(outsider);
            var group=Group.builder().groupName("Places").owner(owner).memberCount(1).maxMembers(4).build(); em.persist(group);
            em.persist(GroupMember.builder().group(group).user(owner).build());
            var date=LocalDate.now(); var trip=Trip.builder().group(group).region("부산").mode(mode).travelStartDate(date).travelEndDate(date.plusDays(1)).build(); em.persist(trip);
            if(opinion) em.persist(Preference.builder().trip(trip).user(owner).transportTypes(Set.of(TransportType.WALK))
                    .travelCategories("[\"CULTURE\",\"CAFE\"]").allergies("[\"새우\"]").budgetMax(90000).build());
            return new Fixture(owner.getId(),outsider.getId(),group.getId(),trip.getId(),"place-"+UUID.randomUUID()+"-",date);
        });
    }
    String auth(long id) { return "Bearer "+tokens.createAccessToken(id); }
    Place place(String id,double lat,String type) { return new Place(id,"임시 표시 이름",new Location(lat,129),List.of(type)); }
    JsonNode data(String response) { return mapper.readTree(response).path("data"); }
    @Test void paginationIsLazyAndDeduplicatedAndSelectionTimelineStoreNoNames() throws Exception {
        var f=seed(true,TripMode.STORY); var calls=new CopyOnWriteArrayList<Search>();
        when(client.searchText(any(),eq(Projection.RECOMMENDATION))).thenAnswer(invocation->{
            Search search=invocation.getArgument(0); calls.add(search);
            return search.pageToken()==null?new Page(List.of(place(f.id(1),35.1,"museum"),place(f.id(2),35.101,"museum")),"next")
                    :new Page(List.of(place(f.id(1),35.1,"museum"),place(f.id(3),35.102,"museum")),null);
        });
        String url=f.base()+"/destinations/recommendations";
        var first=data(mvc.perform(get(url).header("Authorization",auth(f.owner())).param("category","CULTURE").param("limit","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].place_id").value(f.id(1)))
                .andReturn().getResponse().getContentAsString());
        assertThat(calls).hasSize(1); assertThat(calls.getFirst().pageToken()).isNull();
        var second=data(mvc.perform(get(url).header("Authorization",auth(f.owner())).param("category","CULTURE").param("limit","1")
                        .param("cursor",first.path("next_cursor").asString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].place_id").value(f.id(2))).andReturn().getResponse().getContentAsString());
        assertThat(calls).hasSize(1);
        mvc.perform(get(url).header("Authorization",auth(f.owner())).param("category","CULTURE").param("limit","1").param("cursor",second.path("next_cursor").asString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.items[0].place_id").value(f.id(3)));
        assertThat(calls).hasSize(2); assertThat(calls.getLast().pageToken()).isEqualTo("next");
        verify(client,never()).location(anyString());
        String selection=mapper.writeValueAsString(Map.of("destinations",List.of(
                Map.of("place_id",f.id(1),"order",1,"visit_date",f.date().toString(),"category","CULTURE"),
                Map.of("place_id",f.id(2),"order",2,"visit_date",f.date().toString(),"category","CULTURE"))));
        mvc.perform(put(f.base()+"/destinations/selection").header("Authorization",auth(f.owner())).contentType(MediaType.APPLICATION_JSON).content(selection)).andExpect(status().isOk());
        var timeline=data(mvc.perform(post(f.base()+"/itineraries/timeline").header("Authorization",auth(f.owner())).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.days[0].items[0].place_id").value(f.id(1)))
                .andExpect(jsonPath("$.data.days[0].items[0].name").doesNotExist()).andExpect(jsonPath("$.data.days[0].items[0].destination_name").doesNotExist())
                .andReturn().getResponse().getContentAsString());
        long itinerary=timeline.path("itinerary_id").asLong();
        jdbc.update("update google_place set location_cached_at=? where place_id=?",java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(31))),f.id(1));
        mvc.perform(get(f.base()+"/itineraries/"+itinerary).header("Authorization",auth(f.owner())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.days[0].items[0].latitude").isEmpty());
        mvc.perform(get(f.base()+"/itineraries/"+itinerary).header("Authorization",auth(f.outsider()))).andExpect(status().isNotFound());
        mvc.perform(get(url).header("Authorization",auth(f.owner())).param("category","CULTURE").param("cursor",first.path("next_cursor").asString()+"tamper"))
                .andExpect(status().isBadRequest());
    }
    @Test void nearbyUsesCachedBaseRectangleAndFiltersRadiusAndAllergyTypes() throws Exception {
        var f=seed(true,TripMode.STORY);
        when(client.location(f.id(1))).thenReturn(Optional.of(new Location(35.1,129)));
        when(client.searchText(any(),any())).thenReturn(new Page(List.of(place(f.id(1),35.1,"museum"),place(f.id(2),35.1001,"museum"),
                place(f.id(3),36,"museum"),place(f.id(4),35.1002,"seafood_restaurant")),null));
        mvc.perform(get(f.base()+"/destinations/"+f.id(1)+"/recommendations").header("Authorization",auth(f.owner()))
                        .param("category","CULTURE").param("radius_meters","100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.items[0].place_id").value(f.id(2)));
        var search=org.mockito.ArgumentCaptor.forClass(Search.class); verify(client).searchText(search.capture(),eq(Projection.RECOMMENDATION));
        assertThat(search.getValue().bounds()).isNotNull();
        assertThat(places.findById(f.id(3))).isPresent(); // 검색 응답의 좌표 upsert, 추천 결과와 별개
    }
    @Test void unauthorizedIncompleteAndFestivalQueriesDoNotCallPlaces() throws Exception {
        var f=seed(false,TripMode.STORY);
        mvc.perform(get(f.base()+"/destinations/recommendations")).andExpect(status().isUnauthorized());
        mvc.perform(get(f.base()+"/destinations/recommendations").header("Authorization",auth(f.outsider()))).andExpect(status().isNotFound());
        mvc.perform(get(f.base()+"/destinations/recommendations").header("Authorization",auth(f.owner()))).andExpect(status().isConflict());
        var ready=seed(true,TripMode.STORY);
        mvc.perform(get(ready.base()+"/destinations/recommendations").header("Authorization",auth(ready.owner())).param("category","FESTIVAL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
        verifyNoInteractions(client);
    }
}
