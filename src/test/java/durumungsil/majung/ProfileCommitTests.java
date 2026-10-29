package durumungsil.majung;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import durumungsil.majung.domain.group.entity.*;
import durumungsil.majung.domain.group.service.GroupReader;
import durumungsil.majung.domain.preference.entity.Preference;
import durumungsil.majung.domain.preference.profile.*;
import durumungsil.majung.domain.trip.entity.Trip;
import durumungsil.majung.domain.user.entity.User;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SpringBootTest(
        properties =
                "spring.datasource.url=jdbc:h2:mem:majung-profile-events;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=DAY")
class ProfileCommitTests {
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired GroupReader groups;
    @Autowired ProfileLifecycle lifecycle;
    @Autowired PreferenceProfileRepository profiles;
    @MockitoBean ProfileGenerator generator;

    @Test
    void workerStartsAfterCommitAndRollbackNeverDispatches() throws Exception {
        CountDownLatch generated = new CountDownLatch(1);
        when(generator.generate(any(), anyList(), anyString()))
                .thenAnswer(
                        call -> {
                            generated.countDown();
                            return call.getArgument(0);
                        });
        long[] ids =
                tx.execute(
                        status -> {
                            var user =
                                    User.builder().googleSub(UUID.randomUUID().toString()).build();
                            em.persist(user);
                            var group =
                                    Group.builder()
                                            .owner(user)
                                            .groupName("커밋 프로필")
                                            .memberCount(1)
                                            .maxMembers(4)
                                            .build();
                            em.persist(group);
                            em.persist(GroupMember.builder().group(group).user(user).build());
                            var trip =
                                    Trip.builder()
                                            .group(group)
                                            .region("부산")
                                            .travelStartDate(LocalDate.now())
                                            .travelEndDate(LocalDate.now())
                                            .build();
                            em.persist(trip);
                            em.persist(
                                    Preference.builder()
                                            .trip(trip)
                                            .user(user)
                                            .allergies("[]")
                                            .budgetMax(30000)
                                            .build());
                            em.flush();
                            return new long[] {group.getId(), trip.getId()};
                        });
        tx.executeWithoutResult(
                status -> {
                    groups.getGroupForUpdate(ids[0]);
                    lifecycle.invalidate(em.find(Trip.class, ids[1]));
                    verifyNoInteractions(generator);
                });
        assertThat(generated.await(5, TimeUnit.SECONDS)).isTrue();
        for (int i = 0;
                i < 100
                        && profiles.findById(ids[1]).orElseThrow().getStatus()
                                != ProfileStatus.DONE;
                i++) Thread.sleep(20);
        var done = profiles.findById(ids[1]).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(ProfileStatus.DONE);
        tx.executeWithoutResult(
                status -> {
                    groups.getGroupForUpdate(ids[0]);
                    lifecycle.invalidate(em.find(Trip.class, ids[1]));
                    status.setRollbackOnly();
                });
        assertThat(profiles.findById(ids[1]).orElseThrow().getVersion())
                .isEqualTo(done.getVersion());
        verify(generator, times(1)).generate(any(), anyList(), anyString());
    }
}
