package durumungsil.majung.domain.roulette.entity;

import durumungsil.majung.domain.trip.entity.Trip;
import durumungsil.majung.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 여행지 룰렛 / 식당 룰렛 / 룰렛 결과 조회
 */
@Entity
@Table(name = "roulette_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RouletteResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @Enumerated(EnumType.STRING)
    private RouletteType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    private Integer round;

    @CreationTimestamp
    @Column(name = "drawn_at", updatable = false)
    private LocalDateTime drawnAt;

    @Builder
    private RouletteResult(Trip trip, RouletteType type, Store store, Integer round) {
        this.trip = trip;
        this.type = type;
        this.store = store;
        this.round = round;
    }
}
