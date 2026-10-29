package durumungsil.majung.domain.story.service;

import durumungsil.majung.domain.group.service.GroupReader;
import durumungsil.majung.domain.place.*;
import durumungsil.majung.domain.place.service.*;
import durumungsil.majung.domain.preference.entity.*;
import durumungsil.majung.domain.preference.profile.*;
import durumungsil.majung.domain.story.dto.response.*;
import durumungsil.majung.domain.story.repository.DestinationSelectionRepository;
import durumungsil.majung.domain.trip.entity.Trip;
import durumungsil.majung.global.exception.*;
import durumungsil.majung.global.places.GooglePlacesClient;
import durumungsil.majung.global.places.GooglePlacesClient.*;
import durumungsil.majung.global.util.GeoUtils;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;

@Service
@Transactional(readOnly = true)
public class DestinationRecommendationService {
    private final GroupReader groupReader;
    private final ProfileReader profiles;
    private final DestinationSelectionRepository selections;
    private final GooglePlaceService places;
    private final GooglePlacePersistence persistence;
    private final GooglePlacesClient client;
    private final Executor executor;
    private final RecommendationCursor cursors;
    private final Clock clock;

    public DestinationRecommendationService(
            GroupReader groupReader,
            ProfileReader profiles,
            DestinationSelectionRepository selections,
            GooglePlaceService places,
            GooglePlacePersistence persistence,
            GooglePlacesClient client,
            @Qualifier("placesExecutor") Executor executor,
            RecommendationCursor cursors,
            Clock clock) {
        this.groupReader = groupReader;
        this.profiles = profiles;
        this.selections = selections;
        this.places = places;
        this.persistence = persistence;
        this.client = client;
        this.executor = executor;
        this.cursors = cursors;
        this.clock = clock;
    }

    public record Candidate(Place place, TravelCategory category, int distance, double score) {}

    public record QueryPage(PreferenceProfileData.SearchQuery query, String token) {}

    public record PageState(
            long userId,
            long tripId,
            long version,
            String category,
            String sort,
            String baseId,
            long issuedAt,
            DestinationRecommendationsResponse.Area area,
            Bounds bounds,
            List<QueryPage> pending,
            List<Candidate> buffered,
            List<String> seen) {}

    private record Batch(QueryPage request, Page page) {}

    private record Result(
            DestinationRecommendationsResponse.Area area,
            List<Candidate> candidates,
            String nextCursor) {}

    public DestinationRecommendationsResponse recommend(
            Long userId,
            Long groupId,
            Integer limit,
            String categoryValue,
            String sort,
            String cursor) {
        groupReader.getMemberGroup(groupId, userId);
        Trip trip = groupReader.getActiveStoryTrip(groupId);
        var profile = profiles.forTrip(trip);
        var result = search(userId, trip, profile, limit, categoryValue, sort, cursor, null, null);
        return new DestinationRecommendationsResponse(
                trip.getRegion(),
                result.area(),
                result.candidates().stream()
                        .map(
                                c ->
                                        new DestinationRecommendationsResponse.Item(
                                                c.place().id(),
                                                c.place().displayName(),
                                                c.category(),
                                                c.place().location().latitude(),
                                                c.place().location().longitude(),
                                                c.distance(),
                                                c.score(),
                                                PlaceTypeCatalog.label(c.category())
                                                        + " 선호와 거리 기준 추천"))
                        .toList(),
                result.nextCursor());
    }

    public NearbyDestinationsResponse recommendNearby(
            Long userId,
            Long groupId,
            String placeId,
            Integer limit,
            Integer radius,
            String category,
            String cursor) {
        groupReader.getMemberGroup(groupId, userId);
        Trip trip = groupReader.getActiveStoryTrip(groupId);
        if (radius != null && (radius < 100 || radius > 5000)) throw invalid();
        var profile = profiles.forTrip(trip);
        var base = places.requireLocation(placeId);
        TransportType transport = profile.transport().primary();
        int meters =
                radius == null
                        ? Math.max(100, TransportRange.nearbyRadiusMeters(transport, 5000))
                        : radius;
        var area =
                new DestinationRecommendationsResponse.Area(
                        base.getLatitude(), base.getLongitude(), meters, transport, "BASE_PLACE");
        var result =
                search(
                        userId,
                        trip,
                        profile,
                        limit,
                        category,
                        "GROUP_MATCH",
                        cursor,
                        placeId,
                        area);
        var items =
                result.candidates().stream()
                        .map(
                                c ->
                                        new NearbyDestinationsResponse.Item(
                                                c.place().id(),
                                                c.place().displayName(),
                                                c.category(),
                                                c.place().location().latitude(),
                                                c.place().location().longitude(),
                                                c.distance(),
                                                c.score(),
                                                TravelTimeEstimator.minutes(
                                                        transport,
                                                        TravelTimeEstimator.routeMeters(
                                                                c.distance()))))
                        .toList();
        return new NearbyDestinationsResponse(
                new NearbyDestinationsResponse.BaseDestination(
                        placeId, base.getLatitude(), base.getLongitude()),
                meters,
                transport,
                items,
                result.nextCursor());
    }

    private Result search(
            long userId,
            Trip trip,
            PreferenceProfileData profile,
            Integer limit,
            String categoryValue,
            String sort,
            String cursor,
            String baseId,
            DestinationRecommendationsResponse.Area suppliedArea) {
        int size = limit == null ? 20 : limit;
        if (size < 1 || size > 20) throw invalid();
        TravelCategory category = parseCategory(categoryValue);
        String sortKey = sort == null ? "GROUP_MATCH" : sort;
        if (!Set.of("GROUP_MATCH", "DISTANCE").contains(sortKey)) throw invalid();
        if (category == TravelCategory.FESTIVAL) return new Result(suppliedArea, List.of(), null);
        String categoryKey = category == null ? "ALL" : category.name();
        PageState state;
        if (cursor != null && !cursor.isBlank()) {
            state = cursors.decode(cursor);
            if (state.userId() != userId
                    || state.tripId() != trip.getId()
                    || state.version() != profile.version()
                    || !Objects.equals(state.baseId(), baseId)
                    || !state.category().equals(categoryKey)
                    || !state.sort().equals(sortKey)
                    || state.issuedAt() > clock.instant().getEpochSecond()
                    || clock.instant().getEpochSecond() - state.issuedAt() > 900) throw invalid();
            if (suppliedArea != null && suppliedArea.radiusMeters() != state.area().radiusMeters())
                throw invalid();
        } else {
            var area = suppliedArea == null ? selectionArea(trip, profile) : suppliedArea;
            var queries =
                    queries(profile, trip.getRegion(), category).stream()
                            .filter(q -> !profile.avoidPlaceTypes().contains(q.includedType()))
                            .map(q -> new QueryPage(q, null))
                            .toList();
            state =
                    new PageState(
                            userId,
                            trip.getId(),
                            profile.version(),
                            categoryKey,
                            sortKey,
                            baseId,
                            clock.instant().getEpochSecond(),
                            area,
                            area == null ? null : bounds(area),
                            queries,
                            List.of(),
                            List.of());
        }
        List<Candidate> buffered = new ArrayList<>(state.buffered());
        List<QueryPage> next = new ArrayList<>(state.pending());
        var area = state.area();
        Set<String> seen = new LinkedHashSet<>(state.seen());
        if (baseId != null) seen.add(baseId);
        // 잔여 후보를 먼저 소진한다. 다음 Google 페이지는 더 보기 요청에서만 조회한다.
        if (buffered.isEmpty() && !next.isEmpty()) {
            List<CompletableFuture<Batch>> futures = new ArrayList<>();
            try {
                for (QueryPage query : next)
                    futures.add(
                            CompletableFuture.supplyAsync(
                                    () ->
                                            new Batch(
                                                    query,
                                                    client.searchText(
                                                            new Search(
                                                                    query.query().text(),
                                                                    query.query().includedType(),
                                                                    PlaceTypeCatalog.strict(
                                                                            query.query()
                                                                                    .category()),
                                                                    state.bounds(),
                                                                    priceLevels(
                                                                            profile,
                                                                            query.query()
                                                                                    .category()),
                                                                    query.token()),
                                                            Projection.RECOMMENDATION)),
                                    executor));
                List<Batch> batches = futures.stream().map(CompletableFuture::join).toList();
                next = new ArrayList<>();
                List<Place> all =
                        batches.stream().flatMap(batch -> batch.page().places().stream()).toList();
                persistence.cache(all);
                if (area == null) {
                    var first = all.stream().filter(p -> p.location() != null).findFirst();
                    if (first.isPresent()) {
                        var location = first.get().location();
                        var transport = profile.transport().primary();
                        area =
                                new DestinationRecommendationsResponse.Area(
                                        location.latitude(),
                                        location.longitude(),
                                        TransportRange.areaRadiusMeters(transport),
                                        transport,
                                        "TOP_RECOMMENDATION");
                    }
                }
                Set<String> unique = new HashSet<>(seen);
                for (Batch batch : batches) {
                    if (batch.page().nextPageToken() != null
                            && !batch.page().nextPageToken().isBlank())
                        next.add(
                                new QueryPage(
                                        batch.request().query(), batch.page().nextPageToken()));
                    for (Place place : batch.page().places()) {
                        if (place.location() == null
                                || !unique.add(place.id())
                                || place.types().stream()
                                        .anyMatch(profile.avoidPlaceTypes()::contains)
                                || area == null) continue;
                        double distance =
                                GeoUtils.distanceMeters(
                                        area.latitude(),
                                        area.longitude(),
                                        place.location().latitude(),
                                        place.location().longitude());
                        if (distance > area.radiusMeters())
                            continue; // Google 사각형 검색을 실제 원형 반경으로 재필터
                        TravelCategory resolved =
                                PlaceTypeCatalog.categoryOf(
                                        place.types(), batch.request().query().category());
                        double ratio =
                                profile.travelCategories().stream()
                                        .filter(c -> c.category() == resolved)
                                        .mapToDouble(PreferenceProfileData.Category::ratio)
                                        .findFirst()
                                        .orElse(0);
                        double score =
                                Math.round(
                                                (.4
                                                                + .4 * ratio
                                                                + .2
                                                                        * Math.max(
                                                                                0,
                                                                                1
                                                                                        - distance
                                                                                                / area
                                                                                                        .radiusMeters()))
                                                        * 100)
                                        / 100.0;
                        buffered.add(
                                new Candidate(place, resolved, (int) Math.round(distance), score));
                    }
                }
            } catch (CompletionException | RejectedExecutionException error) {
                throw new BusinessException(ErrorCode.PLACES_UNAVAILABLE);
            }
        }
        Comparator<Candidate> comparator =
                Comparator.comparingDouble(Candidate::score)
                        .reversed()
                        .thenComparingInt(Candidate::distance)
                        .thenComparing(c -> c.place().id());
        if (sortKey.equals("DISTANCE"))
            comparator =
                    Comparator.comparingInt(Candidate::distance).thenComparing(c -> c.place().id());
        buffered.sort(comparator);
        List<Candidate> page = List.copyOf(buffered.subList(0, Math.min(size, buffered.size())));
        List<Candidate> remainder = List.copyOf(buffered.subList(page.size(), buffered.size()));
        page.forEach(candidate -> seen.add(candidate.place().id()));
        String nextCursor =
                next.isEmpty() && remainder.isEmpty()
                        ? null
                        : cursors.encode(
                                new PageState(
                                        userId,
                                        trip.getId(),
                                        profile.version(),
                                        categoryKey,
                                        sortKey,
                                        baseId,
                                        state.issuedAt(),
                                        area,
                                        state.bounds(),
                                        next,
                                        remainder,
                                        List.copyOf(seen)));
        return new Result(area, page, nextCursor);
    }

    private DestinationRecommendationsResponse.Area selectionArea(
            Trip trip, PreferenceProfileData profile) {
        List<PlanningPlace> selected =
                selections.findAllByTripIdWithPlace(trip.getId()).stream()
                        .map(
                                s ->
                                        PlanningPlace.of(
                                                places.requireLocation(
                                                        s.getGooglePlace().getPlaceId()),
                                                s.getCategory()))
                        .toList();
        if (selected.isEmpty()) return null;
        PlanningPlace center =
                selected.stream()
                        .min(
                                Comparator.comparingDouble(
                                        candidate ->
                                                selected.stream()
                                                        .mapToDouble(
                                                                other ->
                                                                        GeoUtils.distanceMeters(
                                                                                candidate
                                                                                        .latitude(),
                                                                                candidate
                                                                                        .longitude(),
                                                                                other.latitude(),
                                                                                other.longitude()))
                                                        .sum()))
                        .orElseThrow();
        var transport = profile.transport().primary();
        return new DestinationRecommendationsResponse.Area(
                center.latitude(),
                center.longitude(),
                TransportRange.areaRadiusMeters(transport),
                transport,
                "SELECTION");
    }

    public static List<PreferenceProfileData.SearchQuery> queries(
            PreferenceProfileData profile, String region, TravelCategory category) {
        if (category != null) {
            if (category == TravelCategory.FESTIVAL) return List.of();
            var chosen =
                    profile.searchQueries().stream().filter(q -> q.category() == category).toList();
            return chosen.isEmpty()
                    ? List.of(
                            new PreferenceProfileData.SearchQuery(
                                    region + " " + PlaceTypeCatalog.label(category),
                                    category,
                                    PlaceTypeCatalog.types(category).getFirst()))
                    : chosen;
        }
        Set<TravelCategory> top =
                new HashSet<>(
                        profile.travelCategories().stream()
                                .filter(c -> c.category() != TravelCategory.FOOD)
                                .limit(3)
                                .map(PreferenceProfileData.Category::category)
                                .toList());
        List<PreferenceProfileData.SearchQuery> queries = new ArrayList<>();
        for (var query : profile.searchQueries())
            if (top.contains(query.category())
                    && queries.stream().noneMatch(q -> q.category() == query.category()))
                queries.add(query);
        profile.searchQueries().stream()
                .filter(q -> q.category() == TravelCategory.FOOD)
                .findFirst()
                .ifPresent(queries::add);
        return List.copyOf(queries);
    }

    private static List<String> priceLevels(
            PreferenceProfileData profile, TravelCategory category) {
        if (category != TravelCategory.FOOD && category != TravelCategory.CAFE
                || profile.budget().mealPerPerson() == null) return List.of();
        int meal = profile.budget().mealPerPerson();
        if (meal <= 10000) return List.of("PRICE_LEVEL_FREE", "PRICE_LEVEL_INEXPENSIVE");
        if (meal <= 30000)
            return List.of("PRICE_LEVEL_FREE", "PRICE_LEVEL_INEXPENSIVE", "PRICE_LEVEL_MODERATE");
        return List.of();
    }

    private static Bounds bounds(DestinationRecommendationsResponse.Area area) {
        double latDelta = area.radiusMeters() / 111320.0,
                lonDelta = latDelta / Math.max(.01, Math.cos(Math.toRadians(area.latitude())));
        return new Bounds(
                Math.max(-90, area.latitude() - latDelta),
                Math.max(-180, area.longitude() - lonDelta),
                Math.min(90, area.latitude() + latDelta),
                Math.min(180, area.longitude() + lonDelta));
    }

    private static TravelCategory parseCategory(String value) {
        if (value == null || value.isBlank() || value.equals("ALL")) return null;
        try {
            return TravelCategory.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.INVALID_RECOMMENDATION_QUERY);
    }
}
