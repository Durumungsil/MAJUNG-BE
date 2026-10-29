package durumungsil.majung.domain.story.entity;

import durumungsil.majung.domain.place.entity.GooglePlace;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 타임라인 항목 수정 대상.
 * - 하루 안 순서는 sequence 로 정한다 (day, sequence 순으로 정렬).
 * - VISIT: store, start_at/end_at, stay_minutes 사용
 * - MOVE : store 는 비우고 duration_minutes, distance_meters 사용.
 *          출발지 = 바로 앞(sequence - 1) VISIT, 도착지 = 바로 뒤(sequence + 1) VISIT
 */
@Entity
@Table(
        name = "itinerary_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"itinerary_id", "day", "sequence"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItineraryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itinerary_id")
    private Itinerary itinerary;

    private Integer day;

    private LocalDate date;

    /** 하루 안 순서 (1, 2, 3 ...) */
    @Column(nullable = false)
    private Integer sequence;

    @Enumerated(EnumType.STRING)
    private ItineraryItemType type;

    /** VISIT 만 사용. MOVE 는 null */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "google_place_id", nullable = true)
    private GooglePlace googlePlace;

    @Column(name = "start_at")
    private LocalTime startAt;

    @Column(name = "end_at")
    private LocalTime endAt;

    @Column(name = "stay_minutes")
    private Integer stayMinutes;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "distance_meters")
    private Integer distanceMeters;

    private String memo;

    @Builder
    private ItineraryItem(Itinerary itinerary, Integer day, LocalDate date, Integer sequence, ItineraryItemType type,
                          GooglePlace googlePlace, LocalTime startAt, LocalTime endAt, Integer stayMinutes,
                          Integer durationMinutes, Integer distanceMeters, String memo) {
        this.itinerary = itinerary;
        this.day = day;
        this.date = date;
        this.sequence = sequence;
        this.type = type;
        this.googlePlace = googlePlace;
        this.startAt = startAt;
        this.endAt = endAt;
        this.stayMinutes = stayMinutes;
        this.durationMinutes = durationMinutes;
        this.distanceMeters = distanceMeters;
        this.memo = memo;
    }

    /**
     * 타임라인 항목 수정 (VISIT 의 시간·메모).
     * 하루 활동 시간 검사와 다른 항목과의 겹침 검사는 같은 날 항목을 모두 아는 서비스에서 한다.
     */
    public void changeSchedule(LocalTime startAt, LocalTime endAt, String memo) {
        if (type != ItineraryItemType.VISIT) {
            throw new IllegalStateException("방문 항목만 시간을 수정할 수 있습니다.");
        }
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("종료 시간이 시작 시간보다 늦어야 합니다.");
        }
        this.startAt = startAt;
        this.endAt = endAt;
        this.stayMinutes = (int) Duration.between(startAt, endAt).toMinutes();
        this.memo = memo;
    }
}
