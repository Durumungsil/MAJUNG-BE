package durumungsil.majung.domain.story.entity;

import durumungsil.majung.domain.trip.entity.Trip;
import durumungsil.majung.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 여행지 선택 저장.
 * 여행 회차당 선택 묶음은 항상 하나이며, 저장(PUT)할 때마다 그 회차의 기존 행을 지우고 새로 넣는다.
 */
@Entity
@Table(
        name = "destination_selections",
        uniqueConstraints = @UniqueConstraint(columnNames = {"trip_id", "visit_order"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DestinationSelection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @Column(name = "visit_order")
    private Integer visitOrder;

    @Column(name = "visit_date")
    private LocalDate visitDate;

    @CreationTimestamp
    @Column(name = "selected_at", updatable = false)
    private LocalDateTime selectedAt;

    @Builder
    private DestinationSelection(Trip trip, Store store, Integer visitOrder, LocalDate visitDate) {
        this.trip = trip;
        this.store = store;
        this.visitOrder = visitOrder;
        this.visitDate = visitDate;
    }
}
