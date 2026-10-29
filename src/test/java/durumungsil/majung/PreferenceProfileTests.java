package durumungsil.majung;

import durumungsil.majung.domain.group.entity.*;
import durumungsil.majung.domain.group.service.GroupReader;
import durumungsil.majung.domain.preference.entity.*;
import durumungsil.majung.domain.preference.service.PreferenceAggregator;
import durumungsil.majung.domain.preference.profile.*;
import durumungsil.majung.domain.trip.entity.Trip;
import durumungsil.majung.domain.user.entity.User;
import durumungsil.majung.global.ai.GeminiClient;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class PreferenceProfileTests {
    @Autowired EntityManager em; @Autowired TransactionTemplate tx;
    @Autowired PreferenceProfileRepository profiles; @Autowired ProfileLifecycle lifecycle;
    @Autowired ProfileTransactions transactions; @Autowired ProfileReader reader;
    @Autowired GroupReader groupReader; @Autowired ObjectMapper mapper;
    @MockitoBean ProfileWorker worker;
    record Fixture(long group,long trip) { }
    Fixture seed() {
        return tx.execute(status->{
            var user=User.builder().googleSub(UUID.randomUUID().toString()).build(); em.persist(user);
            var group=Group.builder().groupName("프로필").memberCount(1).maxMembers(4).owner(user).build(); em.persist(group);
            em.persist(GroupMember.builder().group(group).user(user).build());
            var trip=Trip.builder().group(group).region("부산").travelStartDate(LocalDate.now()).travelEndDate(LocalDate.now()).build(); em.persist(trip);
            em.persist(Preference.builder().trip(trip).user(user).transportTypes(Set.of(TransportType.WALK))
                    .travelCategories("[\"CAFE\",\"FESTIVAL\"]").foodPreferences(List.of(new FoodPreference("국밥",5)))
                    .allergies("[\"땅콩\",\"새우\"]").budgetMax(30000).build());
            em.flush(); groupReader.getGroupForUpdate(group.getId()); lifecycle.invalidate(trip);
            return new Fixture(group.getId(),trip.getId());
        });
    }
    @Test void staleWorkerCannotOverwriteNewSurveyAndRulesArePersistedAsJson() {
        var f=seed(); var first=profiles.findById(f.trip()).orElseThrow();
        var oldWork=transactions.claim(new ProfileJob(f.trip(),first.getVersion())).orElseThrow();
        tx.executeWithoutResult(status->{ groupReader.getGroupForUpdate(f.group()); lifecycle.invalidate(em.find(Trip.class,f.trip())); });
        assertThat(transactions.finish(oldWork,oldWork.rules())).isFalse();
        var next=profiles.findById(f.trip()).orElseThrow();
        assertThat(next.getVersion()).isEqualTo(first.getVersion()+1); assertThat(next.getStatus()).isEqualTo(ProfileStatus.PENDING);
        var newWork=transactions.claim(new ProfileJob(f.trip(),next.getVersion())).orElseThrow();
        assertThat(transactions.finish(newWork,newWork.rules())).isTrue();
        var profile=profiles.findById(f.trip()).orElseThrow(); assertThat(profile.getStatus()).isEqualTo(ProfileStatus.DONE);
        assertThat(mapper.readTree(profile.getProfile()).path("generated_by").asString()).isEqualTo("RULE");
        tx.executeWithoutResult(status->{
            var data=reader.forTrip(em.find(Trip.class,f.trip()));
            assertThat(data.budget().mealPerPerson()).isEqualTo(10000);
            assertThat(data.searchQueries()).anyMatch(q->q.category()==TravelCategory.FOOD && q.text().contains("국밥"));
            assertThat(data.travelCategories()).noneMatch(c->c.category()==TravelCategory.FESTIVAL);
            assertThat(data.allergies().normalized()).containsExactly(Allergy.SHELLFISH,Allergy.PEANUT);
        });
    }
    @Test void unconfirmedPaidTierNeverTransmitsAllergiesAndInvalidAiFallsBack() {
        var f=seed(); var work=transactions.claim(new ProfileJob(f.trip(),profiles.findById(f.trip()).orElseThrow().getVersion())).orElseThrow();
        GeminiClient gemini=mock(GeminiClient.class);
        var unconfirmed=new ProfileGenerator(gemini,mapper,false);
        assertThat(unconfirmed.generate(work.rules(),work.rawAllergies(),"부산")).isEqualTo(work.rules()); verifyNoInteractions(gemini);
        when(gemini.isEnabled()).thenReturn(true); when(gemini.generateJson(anyString(),anyMap())).thenReturn(Optional.of("{\"summary\":\"bad\",\"allergies\":{\"normalized\":[\"INVALID\"]}}"));
        var confirmed=new ProfileGenerator(gemini,mapper,true);
        assertThat(confirmed.generate(work.rules(),work.rawAllergies(),"부산")).isEqualTo(work.rules());
        assertThatThrownBy(()->confirmed.validate("{\"summary\":\"요약\",\"allergies\":{\"normalized\":[]},\"avoid_place_types\":[],\"search_queries\":[{\"text\":\"부산 카페\",\"category\":\"CAFE\",\"included_type\":\"cafe\"}]}",work.rules(),List.of(),"부산")).isInstanceOf(IllegalArgumentException.class);
    }
}
