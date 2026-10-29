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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class ExtremeModeApiTests {
    @Autowired EntityManager em; @Autowired TransactionTemplate tx; @Autowired JwtTokenProvider tokens;
    @Autowired WebApplicationContext context; @Autowired ObjectMapper mapper; @Autowired GooglePlaceRepository places;
    @MockitoBean GooglePlacesClient client; @MockitoBean ProfileWorker worker;
    MockMvc mvc;
    record Fixture(long user,long outsider,long group,String prefix) {
        String base(){return "/api/v2/groups/"+group;}
        String id(int i){return prefix+i;}
    }
    @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    Fixture seed(TripMode mode){return tx.execute(status->{
        var user=User.builder().googleSub(UUID.randomUUID().toString()).build();em.persist(user);
        var outsider=User.builder().googleSub(UUID.randomUUID().toString()).build();em.persist(outsider);
        var group=Group.builder().groupName("룰렛").owner(user).memberCount(1).maxMembers(4).build();em.persist(group);
        em.persist(GroupMember.builder().group(group).user(user).build());
        var trip=Trip.builder().group(group).region("부산").mode(mode).travelStartDate(LocalDate.now()).travelEndDate(LocalDate.now()).build();em.persist(trip);
        em.persist(Preference.builder().trip(trip).user(user).transportTypes(Set.of(TransportType.WALK)).travelCategories("[\"CULTURE\"]")
                .allergies("[\"새우\"]").excludedCategories("[\"SEAFOOD_RESTAURANT\"]").build());
        return new Fixture(user.getId(),outsider.getId(),group.getId(),"roulette-"+UUID.randomUUID()+"-");
    });}
    String auth(long id){return "Bearer "+tokens.createAccessToken(id);}
    @Test void idsOnlyDrawPersistsNoNamesOrLocationsAndExcludesPriorRounds() throws Exception {
        var f=seed(TripMode.EXTREME);
        when(client.searchText(any(),eq(Projection.IDS_ONLY))).thenReturn(new Page(List.of(new Place(f.id(1),null,null,List.of()),new Place(f.id(2),null,null,List.of())),"ignored-next"));
        var first=mapper.readTree(mvc.perform(post(f.base()+"/roulette/destinations").header("Authorization",auth(f.user()))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("category","CULTURE","exclude_place_ids",List.of(f.id(2))))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.place.place_id").value(f.id(1)))
                .andExpect(jsonPath("$.data.place.name").doesNotExist()).andExpect(jsonPath("$.data.place.address").doesNotExist())
                .andReturn().getResponse().getContentAsString()).path("data");
        assertThat(places.findById(f.id(1)).orElseThrow().getLatitude()).isNull();
        mvc.perform(post(f.base()+"/roulette/destinations").header("Authorization",auth(f.user())).contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"CULTURE\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.place.place_id").value(f.id(2))).andExpect(jsonPath("$.data.round").value(2));
        mvc.perform(post(f.base()+"/roulette/destinations").header("Authorization",auth(f.user())).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
        long result=first.path("roulette_id").asLong();
        mvc.perform(get(f.base()+"/roulette/"+result).header("Authorization",auth(f.user())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.place.place_id").value(f.id(1)));
        mvc.perform(get(f.base()+"/roulette/"+result).header("Authorization",auth(f.outsider()))).andExpect(status().isNotFound());
        verify(client,never()).location(anyString());
    }
    @Test void constraintsUseNormalizedProfileAndWrongRegionNeverCallsGoogle() throws Exception {
        var f=seed(TripMode.EXTREME);
        mvc.perform(get(f.base()+"/constraints/minimum").header("Authorization",auth(f.user())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.allergies[0]").value("SHELLFISH"))
                .andExpect(jsonPath("$.data.excluded_categories").isArray());
        mvc.perform(post(f.base()+"/roulette/restaurants").header("Authorization",auth(f.user())).contentType(MediaType.APPLICATION_JSON).content("{\"region\":\"서울\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(client);
    }
    @Test void destinationIdMustBeStringAndStoryTripCannotDraw() throws Exception {
        var f=seed(TripMode.STORY);
        mvc.perform(post(f.base()+"/roulette/destinations").header("Authorization",auth(f.user())).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
        verifyNoInteractions(client);
    }
}
