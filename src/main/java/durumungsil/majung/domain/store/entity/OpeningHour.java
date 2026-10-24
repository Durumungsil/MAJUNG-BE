package durumungsil.majung.domain.store.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * 요일별 영업 시간 한 구간.
 * 브레이크 타임이 있으면 같은 요일에 구간을 여러 개 저장한다.
 * closeTime 이 openTime 보다 이르거나 같으면 자정을 넘겨 다음 날까지 영업하는 것으로 본다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpeningHour {

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime;

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime;

    public OpeningHour(DayOfWeek dayOfWeek, LocalTime openTime, LocalTime closeTime) {
        if (dayOfWeek == null || openTime == null || closeTime == null) {
            throw new IllegalArgumentException("요일과 영업 시작/종료 시간은 필수입니다.");
        }
        this.dayOfWeek = dayOfWeek;
        this.openTime = openTime;
        this.closeTime = closeTime;
    }

    public boolean isOvernight() {
        return !closeTime.isAfter(openTime);
    }

    /** 주어진 요일·시각이 이 구간 안에 있는지 */
    public boolean contains(DayOfWeek day, LocalTime time) {
        if (!isOvernight()) {
            return day == dayOfWeek && !time.isBefore(openTime) && time.isBefore(closeTime);
        }
        // 자정을 넘기는 구간: 당일 openTime 이후 또는 다음 날 closeTime 이전
        return (day == dayOfWeek && !time.isBefore(openTime))
                || (day == dayOfWeek.plus(1) && time.isBefore(closeTime));
    }
}
