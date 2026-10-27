package durumungsil.majung.domain.roulette.entity;

import durumungsil.majung.domain.trip.entity.Trip;
import durumungsil.majung.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * 여행지 룰렛 / 식당 룰렛 / 룰렛 결과 조회
 */
@Entity
@Table(name = "roulette_results", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"trip_id", "type", "round"}),
        @UniqueConstraint(columnNames = {"trip_id", "type", "store_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RouletteResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RouletteType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false)
    private Integer round;

    @CreationTimestamp
    @Column(name = "drawn_at", updatable = false)
    private Instant drawnAt;

    @Builder
    private RouletteResult(Trip trip, RouletteType type, Store store, Integer round) {
        if (trip == null || type == null || store == null || round == null || round < 1) {
            throw new IllegalArgumentException("여행 회차, 룰렛 종류, 장소와 1 이상의 라운드는 필수입니다.");
        }
        this.trip = trip;
        this.type = type;
        this.store = store;
        this.round = round;
    }
}
