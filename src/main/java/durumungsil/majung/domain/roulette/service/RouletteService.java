package durumungsil.majung.domain.roulette.service;

import durumungsil.majung.domain.group.service.GroupReader;
import durumungsil.majung.domain.place.PlaceTypeCatalog;
import durumungsil.majung.domain.place.service.GooglePlaceService;
import durumungsil.majung.domain.preference.entity.TravelCategory;
import durumungsil.majung.domain.preference.profile.*;
import durumungsil.majung.domain.roulette.dto.request.DrawRouletteRequest;
import durumungsil.majung.domain.roulette.dto.response.*;
import durumungsil.majung.domain.roulette.entity.*;
import durumungsil.majung.domain.roulette.repository.RouletteResultRepository;
import durumungsil.majung.domain.story.service.DestinationRecommendationService;
import durumungsil.majung.global.exception.*;
import durumungsil.majung.global.places.GooglePlacesClient;
import durumungsil.majung.global.places.GooglePlacesClient.*;
import durumungsil.majung.global.util.RegionAliases;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RouletteService {
    private final GroupReader groupReader;
    private final ProfileReader profiles;
    private final RouletteResultRepository results;
    private final GooglePlacesClient client;
    private final GooglePlaceService places;

    @Transactional
    public DrawRouletteResponse draw(
            Long userId, Long groupId, RouletteType type, DrawRouletteRequest request) {
        groupReader.getMemberGroupForUpdate(groupId, userId);
        var trip = groupReader.getActiveTrip(groupId);
        MinimumConstraintsService.validateMode(trip);
        if (request.region() != null
                && !RegionAliases.of(trip.getRegion()).contains(request.region().strip()))
            throw new BusinessException(ErrorCode.INVALID_ROULETTE_REGION);
        var profile = profiles.forTrip(trip);
        TravelCategory category = null;
        if (request.category() != null) {
            try {
                category = TravelCategory.valueOf(request.category());
            } catch (IllegalArgumentException error) {
                throw new BusinessException(ErrorCode.INVALID_RECOMMENDATION_QUERY);
            }
        }
        ErrorCode empty =
                type == RouletteType.RESTAURANT
                        ? ErrorCode.NO_ROULETTE_RESTAURANT
                        : ErrorCode.NO_ROULETTE_DESTINATION;
        if (category == TravelCategory.FESTIVAL
                || type == RouletteType.RESTAURANT
                        && category != null
                        && category != TravelCategory.FOOD) throw new BusinessException(empty);
        List<PreferenceProfileData.SearchQuery> queries;
        if (type == RouletteType.RESTAURANT)
            queries =
                    DestinationRecommendationService.queries(
                            profile, trip.getRegion(), TravelCategory.FOOD);
        else if (category != null)
            queries = DestinationRecommendationService.queries(profile, trip.getRegion(), category);
        else {
            queries =
                    profile.searchQueries().stream()
                            .filter(q -> q.category() != TravelCategory.FOOD)
                            .toList();
            if (queries.isEmpty())
                queries =
                        DestinationRecommendationService.queries(
                                profile, trip.getRegion(), TravelCategory.SIGHTSEEING);
        }
        queries =
                queries.stream()
                        .filter(
                                q ->
                                        !profile.avoidPlaceTypes().contains(q.includedType())
                                                && PlaceTypeCatalog.allows(
                                                        q.category(), q.includedType()))
                        .toList();
        if (queries.isEmpty()
                || type == RouletteType.RESTAURANT
                        && profile.allergies().normalized().contains(Allergy.UNKNOWN))
            throw new BusinessException(empty);
        // 한 번의 추첨에 검색 한 번. 첫 페이지 상위 결과 중 제외되지 않은 장소를 균등 추첨한다.
        var query = queries.get(ThreadLocalRandom.current().nextInt(queries.size()));
        var page =
                client.searchText(
                        new Search(query.text(), query.includedType(), true, null, List.of(), null),
                        Projection.IDS_ONLY);
        var previous = results.findAllByTripIdAndTypeOrderByRoundAsc(trip.getId(), type);
        Set<String> excluded = new HashSet<>(request.excludePlaceIds());
        previous.forEach(p -> excluded.add(p.getGooglePlace().getPlaceId()));
        var candidates =
                page.places().stream()
                        .map(Place::id)
                        .distinct()
                        .filter(id -> !excluded.contains(id))
                        .toList();
        if (candidates.isEmpty()) throw new BusinessException(empty);
        String id = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        var place = places.reference(id);
        int round = previous.stream().mapToInt(RouletteResult::getRound).max().orElse(0) + 1;
        var result =
                results.saveAndFlush(
                        RouletteResult.builder()
                                .trip(trip)
                                .type(type)
                                .googlePlace(place)
                                .category(query.category())
                                .round(round)
                                .build());
        return new DrawRouletteResponse(
                result.getId(),
                type,
                RoulettePlaceResponse.of(result),
                type == RouletteType.RESTAURANT
                        ? profile.allergies().normalized().stream().map(Enum::name).toList()
                        : null,
                round,
                result.getDrawnAt());
    }

    public RouletteResultResponse get(Long userId, Long groupId, Long rouletteId) {
        groupReader.getMemberGroup(groupId, userId);
        var result =
                results.findByIdAndTripGroupId(rouletteId, groupId)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.ROULETTE_RESULT_NOT_FOUND));
        MinimumConstraintsService.validateMode(result.getTrip());
        return RouletteResultResponse.of(result);
    }
}
