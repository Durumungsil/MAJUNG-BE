package durumungsil.majung.domain.itinerary;

import durumungsil.majung.domain.preference.TransportType;
import durumungsil.majung.domain.trip.Trip;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * AI 타임라인 생성 / 타임라인 조회
 */
@Entity
@Table(name = "itineraries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Itinerary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** 생성 요청에서 받은 교통수단 (이동 시간 계산 기준) */
    @Enumerated(EnumType.STRING)
    @Column(name = "transport_type")
    private TransportType transportType;

    /** 생성 요청에서 받은 하루 일정 시작 시각 (항목 수정 시 범위 검사 기준) */
    @Column(name = "day_start_time")
    private LocalTime dayStartTime;

    /** 생성 요청에서 받은 하루 일정 종료 시각 */
    @Column(name = "day_end_time")
    private LocalTime dayEndTime;

    @CreationTimestamp
    @Column(name = "generated_at", updatable = false)
    private LocalDateTime generatedAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    private Itinerary(Trip trip, LocalDate startDate, LocalDate endDate,
                      TransportType transportType, LocalTime dayStartTime, LocalTime dayEndTime) {
        this.trip = trip;
        this.startDate = startDate;
        this.endDate = endDate;
        this.transportType = transportType;
        this.dayStartTime = dayStartTime;
        this.dayEndTime = dayEndTime;
    }

    /** 일정이 하루 활동 시간(day_start_time ~ day_end_time) 안에 있는지. 기준이 없으면 true */
    public boolean isWithinDayHours(LocalTime startAt, LocalTime endAt) {
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("종료 시간이 시작 시간보다 늦어야 합니다.");
        }
        if (dayStartTime == null || dayEndTime == null) {
            return true;
        }
        return !startAt.isBefore(dayStartTime) && !endAt.isAfter(dayEndTime);
    }
}
