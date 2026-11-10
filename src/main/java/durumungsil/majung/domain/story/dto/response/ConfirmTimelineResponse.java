package durumungsil.majung.domain.story.dto.response;

import durumungsil.majung.domain.trip.entity.Trip;
import durumungsil.majung.domain.trip.entity.TripStatus;

import java.time.LocalDateTime;

/** 타임라인 확정 결과. status 는 IN_PROGRESS */
public record ConfirmTimelineResponse(
        Long groupId, Long tripId, Long itineraryId, TripStatus status, LocalDateTime confirmedAt) {

    public static ConfirmTimelineResponse of(Long groupId, Long itineraryId, Trip trip) {
        return new ConfirmTimelineResponse(
                groupId, trip.getId(), itineraryId, trip.getStatus(), trip.getUpdatedAt());
    }
}
