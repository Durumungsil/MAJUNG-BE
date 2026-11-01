package durumungsil.majung.domain.place.service;

import durumungsil.majung.domain.place.repository.GooglePlaceRepository;
import durumungsil.majung.global.places.GooglePlacesClient.Place;
import durumungsil.majung.global.places.RestGooglePlacesClient;

import jakarta.persistence.EntityManager;

import lombok.RequiredArgsConstructor;

import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class GooglePlacePersistence {
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final GooglePlaceRepository repository;
    private final Clock clock;

    /**
     * 원자적 upsert. IDs-only 결과로 이미 저장된 좌표를 덮어쓰지 않는다. 호출자의 트랜잭션이 있으면 참여하므로 영속성 컨텍스트의 엔티티는 그대로
     * 관리 상태로 남으며, 이때 호출자는 쓰기 가능한 트랜잭션이어야 한다. 호출자 트랜잭션이 없으면 이 호출만의 짧은 트랜잭션으로 커밋한다.
     *
     * <p>동시에 겹치는 장소 묶음을 캐시해도 행 잠금 순서가 엇갈리지 않도록 장소 ID로 중복을 합치고 ID 오름차순으로 쓴다.
     */
    @Transactional
    public void cache(List<Place> places) {
        Map<String, Place> ordered = new TreeMap<>();
        for (Place place : places) {
            if (!RestGooglePlacesClient.validId(place.id()))
                throw new IllegalArgumentException("유효하지 않은 장소 ID");
            // 같은 ID가 여러 번 오면 마지막 좌표를 쓰고, 좌표 없는 결과는 좌표 있는 결과를 덮지 않는다.
            ordered.merge(place.id(), place, (kept, next) -> next.location() != null ? next : kept);
        }
        boolean postgres =
                entityManager
                                .getEntityManagerFactory()
                                .unwrap(SessionFactoryImplementor.class)
                                .getJdbcServices()
                                .getDialect()
                        instanceof PostgreSQLDialect;
        for (Place place : ordered.values()) {
            if (place.location() == null) {
                if (postgres)
                    jdbc.update(
                            "insert into google_place(place_id) values (?) on conflict(place_id) do"
                                + " nothing",
                            place.id());
                else
                    jdbc.update(
                            "merge into google_place target using (values (cast(? as varchar)))"
                                + " src(place_id) on target.place_id=src.place_id when not matched"
                                + " then insert(place_id) values(src.place_id)",
                            place.id());
            } else {
                String sql =
                        postgres
                                ? "insert into"
                                      + " google_place(place_id,latitude,longitude,location_cached_at)"
                                      + " values (?,?,?,?) on conflict(place_id) do update set"
                                      + " latitude=excluded.latitude,longitude=excluded.longitude,location_cached_at=excluded.location_cached_at"
                                : "merge into"
                                      + " google_place(place_id,latitude,longitude,location_cached_at)"
                                      + " key(place_id) values (?,?,?,?)";
                jdbc.update(
                        sql,
                        place.id(),
                        place.location().latitude(),
                        place.location().longitude(),
                        Timestamp.from(clock.instant()));
            }
        }
    }

    @Scheduled(fixedDelayString = "${google.places.expiry-interval-ms:3600000}")
    @Transactional
    public void expireCoordinates() {
        repository.expireCoordinates(clock.instant().minus(Duration.ofDays(30)));
    }
}
