package durumungsil.majung;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import durumungsil.majung.domain.place.repository.GooglePlaceRepository;
import durumungsil.majung.domain.place.service.*;
import durumungsil.majung.global.places.GooglePlacesClient;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.*;
import java.util.List;

@SpringBootTest
class GooglePlacePersistenceTests {
    @Autowired GooglePlacePersistence persistence;
    @Autowired GooglePlaceRepository repository;
    @Autowired GooglePlaceService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean GooglePlacesClient client;

    @Test
    void idsOnlyUpsertPreservesLocationAndExpiryKeepsPermanentIdThenRefetches() {
        String id = "cache-test";
        persistence.cache(
                List.of(
                        new GooglePlacesClient.Place(
                                id,
                                "never-persist",
                                new GooglePlacesClient.Location(35.1, 129.1),
                                List.of("cafe"))));
        persistence.cache(List.of(new GooglePlacesClient.Place(id, null, null, List.of())));
        assertThat(repository.findById(id).orElseThrow().getLatitude()).isEqualTo(35.1);
        jdbc.update(
                "update google_place set location_cached_at=? where place_id=?",
                java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(31))),
                id);
        persistence.expireCoordinates();
        var expired = repository.findById(id).orElseThrow();
        assertThat(expired.getLatitude()).isNull();
        assertThat(expired.getLongitude()).isNull();
        when(client.location(id))
                .thenReturn(java.util.Optional.of(new GooglePlacesClient.Location(35.2, 129.2)));
        assertThat(service.requireLocation(id).getLatitude()).isEqualTo(35.2);
        assertThat(service.requireLocation(id).getLatitude()).isEqualTo(35.2);
        verify(client, times(1)).location(id);
    }
}
