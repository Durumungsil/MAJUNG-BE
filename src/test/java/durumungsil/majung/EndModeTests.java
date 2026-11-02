package durumungsil.majung;

import durumungsil.majung.domain.group.dto.response.EndModeResponse;
import durumungsil.majung.domain.group.entity.Group;
import durumungsil.majung.domain.group.entity.GroupMember;
import durumungsil.majung.domain.trip.dto.request.CreateTripRequest;
import durumungsil.majung.domain.trip.entity.Trip;
import durumungsil.majung.domain.trip.entity.TripMode;
import durumungsil.majung.domain.trip.entity.TripStatus;
import durumungsil.majung.domain.trip.repository.TripRepository;
import durumungsil.majung.domain.trip.service.TripService;
import durumungsil.majung.domain.user.entity.User;
import durumungsil.majung.global.exception.BusinessException;
import durumungsil.majung.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 모드 종료 API (POST /api/v1/groups/{groupId}/mode/end) */
@SpringBootTest
class EndModeTests {
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired TripService tripService;
    @Autowired TripRepository trips;

    record Fixture(long owner, long member, long group, long trip) { }

    Fixture seed(TripMode mode) {
        return tx.execute(status -> {
            User owner = User.builder().googleSub(UUID.randomUUID().toString()).userName("방장").build();
            User member = User.builder().googleSub(UUID.randomUUID().toString()).userName("멤버").build();
            em.persist(owner);
            em.persist(member);
            Group group = Group.builder().groupName("그룹").owner(owner).memberCount(2).maxMembers(4)
                    .inviteCode("abc123").build();
            group.issueInviteCode(UUID.randomUUID().toString(), LocalDateTime.now().minusDays(2));
            em.persist(group);
            em.persist(GroupMember.builder().group(group).user(owner).build());
            em.persist(GroupMember.builder().group(group).user(member).build());
            Trip trip = Trip.builder().group(group).region("BUSAN").mode(mode).travelStartDate(LocalDate.now())
                    .travelEndDate(LocalDate.now().plusDays(1)).build();
            em.persist(trip);
            return new Fixture(owner.getId(), member.getId(), group.getId(), trip.getId());
        });
    }

    @ParameterizedTest
    @EnumSource(TripMode.class)
    void ownerEndsEveryModeAndCanStartANewTrip(TripMode mode) {
        Fixture data = seed(mode);

        EndModeResponse response = tripService.endMode(data.owner(), data.group());

        assertEquals(data.trip(), response.tripId());
        assertEquals(mode, response.mode());
        assertEquals(TripStatus.COMPLETED, response.status());
        assertNotNull(response.endedAt());
        assertEquals(TripStatus.COMPLETED, trips.findById(data.trip()).orElseThrow().getStatus());

        // 종료한 뒤에는 새 여행 회차를 만들 수 있다
        tripService.createTrip(data.owner(), data.group(),
                new CreateTripRequest("SEOUL", LocalDate.now(), LocalDate.now()));
    }

    @Test
    void onlyOwnerCanEndMode() {
        Fixture data = seed(TripMode.STORY);
        assertError(ErrorCode.NOT_OWNER_END_MODE, () -> tripService.endMode(data.member(), data.group()));
        assertEquals(TripStatus.PLANNING, trips.findById(data.trip()).orElseThrow().getStatus());
    }

    @Test
    void nonMemberGets404() {
        Fixture data = seed(TripMode.STORY);
        Fixture other = seed(TripMode.STORY);
        assertError(ErrorCode.GROUP_NOT_FOUND, () -> tripService.endMode(other.owner(), data.group()));
    }

    @Test
    void tripWithoutModeCannotBeEnded() {
        Fixture data = seed(null);
        assertError(ErrorCode.MODE_NOT_SELECTED, () -> tripService.endMode(data.owner(), data.group()));
    }

    @Test
    void endingTwiceFailsBecauseNoActiveTrip() {
        Fixture data = seed(TripMode.ADVENTURE);
        tripService.endMode(data.owner(), data.group());
        assertError(ErrorCode.NO_ACTIVE_TRIP, () -> tripService.endMode(data.owner(), data.group()));
    }

    private static void assertError(ErrorCode expected, Runnable action) {
        BusinessException error = assertThrows(BusinessException.class, action::run);
        assertEquals(expected, error.getErrorCode());
    }
}
