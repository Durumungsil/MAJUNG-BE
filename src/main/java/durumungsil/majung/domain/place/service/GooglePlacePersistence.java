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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GooglePlacePersistence {
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final GooglePlaceRepository repository;
    private final Clock clock;

    /** 원자적 upsert. IDs-only 결과로 이미 저장된 좌표를 덮어쓰지 않는다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cache(List<Place> places) {
        boolean postgres =
                entityManager
                                .getEntityManagerFactory()
                                .unwrap(SessionFactoryImplementor.class)
                                .getJdbcServices()
                                .getDialect()
                        instanceof PostgreSQLDialect;
        for (Place place : places) {
            if (!RestGooglePlacesClient.validId(place.id()))
                throw new IllegalArgumentException("유효하지 않은 장소 ID");
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
        entityManager.clear();
    }

    @Scheduled(fixedDelayString = "${google.places.expiry-interval-ms:3600000}")
    @Transactional
    public void expireCoordinates() {
        repository.expireCoordinates(clock.instant().minus(Duration.ofDays(30)));
    }
}
