package durumungsil.majung.domain.place.service;

import durumungsil.majung.domain.place.entity.GooglePlace;
import durumungsil.majung.domain.place.repository.GooglePlaceRepository;
import durumungsil.majung.global.exception.BusinessException;
import durumungsil.majung.global.exception.ErrorCode;
import durumungsil.majung.global.places.GooglePlacesClient;

import jakarta.persistence.EntityManager;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GooglePlaceService {
    private final GooglePlaceRepository repository;
    private final GooglePlacePersistence persistence;
    private final GooglePlacesClient client;
    private final Clock clock;
    private final EntityManager entityManager;

    public GooglePlace requireLocation(String id) {
        GooglePlace cached = repository.findById(id).orElse(null);
        if (cached != null && cached.hasFreshLocation(clock.instant())) return cached;
        GooglePlacesClient.Location location =
                client.location(id)
                        .orElseThrow(() -> new BusinessException(ErrorCode.DESTINATION_NOT_FOUND));
        persistence.cache(List.of(new GooglePlacesClient.Place(id, null, location, List.of())));
        if (cached != null && entityManager.contains(cached)) {
            entityManager.refresh(cached);
            return cached;
        }
        return repository.findById(id).orElseThrow();
    }

    public GooglePlace reference(String id) {
        persistence.cache(List.of(new GooglePlacesClient.Place(id, null, null, List.of())));
        return repository.getReferenceById(id);
    }
}
