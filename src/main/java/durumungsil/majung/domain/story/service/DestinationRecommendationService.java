package durumungsil.majung.domain.story.service;

import durumungsil.majung.domain.group.service.GroupReader;
import durumungsil.majung.domain.preference.entity.TransportType;
import durumungsil.majung.domain.preference.entity.TravelCategory;
import durumungsil.majung.domain.store.entity.Store;
import durumungsil.majung.domain.store.repository.StoreRepository;
import durumungsil.majung.domain.store.entity.Type;
import durumungsil.majung.domain.trip.entity.Trip;
import durumungsil.majung.domain.story.dto.response.DestinationRecommendationsResponse;
import durumungsil.majung.domain.story.dto.response.NearbyDestinationsResponse;
import durumungsil.majung.domain.story.entity.DestinationSelection;
import durumungsil.majung.domain.story.repository.DestinationSelectionRepository;
import durumungsil.majung.global.exception.BusinessException;
import durumungsil.majung.global.exception.ErrorCode;
import durumungsil.majung.global.util.RegionAliases;
import durumungsil.majung.domain.preference.repository.PreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 스토리모드 여행지 추천 (임시 규칙).
 * - 범위: 그룹 교통수단으로 다니기 적당한 반경({@link TransportRange#areaRadiusMeters}) 안.
 *   중심은 저장한 여행지 선택 중 가운데 장소(medoid), 선택이 없으면 취향 점수 1위 장소
 * - 후보: 여행 지역 + 여행지로 추천할 수 있는 업종 + 범위 안 장소 중 중심에서 가까운
 *   {@link StoreRepository#MAX_REGION_CANDIDATES}개
 * - 그룹 조건 필터: {@link GroupPlaceFilter} (지금은 통과만 시킨다)
 * - 점수: 그 카테고리를 고른 멤버 비율 + 중심에서 가까움 (2차 추천은 기준 여행지에서 가까움)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DestinationRecommendationService {

    static final int DEFAULT_LIMIT = 5;
    static final int MAX_LIMIT = 50;
    static final int MIN_RADIUS_METERS = 100;
    static final int MAX_RADIUS_METERS = 5000;

    static final String SORT_GROUP_MATCH = "GROUP_MATCH";
    static final String SORT_RATING = "RATING";

    static final String AREA_BASED_ON_SELECTION = "SELECTION";
    static final String AREA_BASED_ON_TOP_RECOMMENDATION = "TOP_RECOMMENDATION";

    /** 교통수단 취향이 없을 때 (타임라인 기본값과 같다) */
    static final TransportType DEFAULT_LIST_TRANSPORT = TransportType.BUS;
    /** 2차 추천에서 교통수단 취향이 없을 때 */
    static final TransportType DEFAULT_NEARBY_TRANSPORT = TransportType.WALK;

    /** 위도 1도의 거리(m) */
    private static final double METERS_PER_DEGREE = 111_320;

    private final GroupReader groupReader;
    private final StoreRepository storeRepository;
    private final PreferenceRepository preferenceRepository;
    private final DestinationSelectionRepository selectionRepository;
    private final GroupPlaceFilter groupPlaceFilter;

    /**
     * 여행지 리스트 추천.
     * 카테고리를 지정하지 않으면 그룹 취향으로 점수를 매기고, 취향을 입력한 멤버도 없으면 409.
     * 추천 범위 밖(교통수단별 반경 밖)의 장소는 추천하지 않는다.
     */
    public DestinationRecommendationsResponse recommend(Long userId, Long groupId, Integer limit,
                                                        List<String> categoryValues, String sort, String cursor) {
        groupReader.getMemberGroup(groupId, userId);
        Trip trip = groupReader.getActiveStoryTrip(groupId);
        int size = validateLimit(limit);
        int offset = parseCursor(cursor);
        String sortKey = parseSort(sort);
        Set<TravelCategory> requested = parseCategories(categoryValues);

        GroupTaste taste = GroupTaste.from(preferenceRepository.findAllOfCurrentMembers(trip.getId(), groupId));
        if (taste.isEmpty() && requested.isEmpty()) {
            throw new BusinessException(ErrorCode.PREFERENCE_NOT_MERGED);
        }

        Set<Type> types = requested.isEmpty()
                ? DestinationCategories.recommendableTypes()
                : DestinationCategories.typesOf(requested);
        List<String> regions = RegionAliases.of(trip.getRegion());
        if (types.isEmpty() || regions.isEmpty()) {
            return new DestinationRecommendationsResponse(trip.getRegion(), null, List.of(), null);
        }

        TransportType transport = taste.primaryTransportOr(DEFAULT_LIST_TRANSPORT);
        int radius = TransportRange.areaRadiusMeters(transport);
        DestinationRecommendationsResponse.Area area = selectionArea(trip, radius, transport)
                .orElseGet(() -> topRecommendationArea(trip, regions, types, taste, radius, transport));
        if (area == null) {
            return new DestinationRecommendationsResponse(trip.getRegion(), null, List.of(), null);
        }

        double latitude = area.latitude();
        double longitude = area.longitude();
        Bounds bounds = Bounds.around(latitude, longitude, radius);
        List<Store> candidates = storeRepository.findInRegionsNearAndTypes(regions, types,
                        bounds.minLatitude(), bounds.maxLatitude(), bounds.minLongitude(), bounds.maxLongitude(),
                        latitude, longitude, bounds.longitudeScale(), StoreRepository.regionCandidateLimit()).stream()
                .filter(DestinationRecommendationService::hasCoordinates)
                .toList();

        List<Scored> ranked = groupPlaceFilter.filter(trip, candidates).stream()
                .map(store -> scoreByTaste(store, taste, store.distanceMetersFrom(latitude, longitude), radius))
                .filter(scored -> scored.distance() <= radius)
                .sorted(SORT_RATING.equals(sortKey) ? BY_RATING : BY_GROUP_MATCH)
                .toList();

        int from = Math.min(offset, ranked.size());
        int to = Math.min(from + size, ranked.size());
        List<DestinationRecommendationsResponse.Item> items = ranked.subList(from, to).stream()
                .map(Scored::toItem)
                .toList();
        String nextCursor = to < ranked.size() ? String.valueOf(to) : null;
        return new DestinationRecommendationsResponse(trip.getRegion(), area, items, nextCursor);
    }

    /**
     * 저장한 여행지 선택이 있으면 그 중 나머지 장소들과의 거리 합이 가장 작은 장소(medoid)를 중심으로 쓴다.
     * 평균 좌표를 쓰면 선택이 멀리 떨어진 두 묶음일 때 어느 장소와도 가깝지 않은 곳이 중심이 되기 때문이다.
     * 거리 합이 같으면 방문일·방문 순서가 앞선 장소.
     */
    private Optional<DestinationRecommendationsResponse.Area> selectionArea(Trip trip, int radius,
                                                                           TransportType transport) {
        List<Store> selected = selectionRepository.findAllByTripIdWithStore(trip.getId()).stream()
                .map(DestinationSelection::getStore)
                .filter(DestinationRecommendationService::hasCoordinates)
                .toList();
        Store center = null;
        double best = Double.MAX_VALUE;
        for (Store candidate : selected) {
            double total = 0;
            for (Store other : selected) {
                total += candidate.distanceMetersFrom(other.getLatitude(), other.getLongitude());
            }
            if (total < best - 1e-6) {
                best = total;
                center = candidate;
            }
        }
        if (center == null) {
            return Optional.empty();
        }
        return Optional.of(new DestinationRecommendationsResponse.Area(center.getLatitude(), center.getLongitude(),
                radius, transport, AREA_BASED_ON_SELECTION));
    }

    /**
     * 선택이 없으면 지역 후보(평점 상위) 중 취향 점수 1위 장소를 중심으로 쓴다
     * (취향 비율 → 평점 → 장소 ID 순, 페이지를 넘겨도 같은 중심이 나오도록 결정적으로 고른다). 후보가 없으면 null.
     */
    private DestinationRecommendationsResponse.Area topRecommendationArea(Trip trip, List<String> regions,
                                                                         Set<Type> types, GroupTaste taste,
                                                                         int radius, TransportType transport) {
        return groupPlaceFilter.filter(trip,
                        storeRepository.findInRegionsAndTypes(regions, types, StoreRepository.regionCandidateLimit()))
                .stream()
                .filter(DestinationRecommendationService::hasCoordinates)
                .min(Comparator.comparingDouble(
                                (Store store) -> -taste.ratio(DestinationCategories.of(store.getDestinationType())))
                        .thenComparing(Store::getRating, RATING_DESC_NULLS_LAST)
                        .thenComparing(Store::getDestinationId))
                .map(store -> new DestinationRecommendationsResponse.Area(store.getLatitude(), store.getLongitude(),
                        radius, transport, AREA_BASED_ON_TOP_RECOMMENDATION))
                .orElse(null);
    }

    /**
     * 2차 추천 - 기준 여행지에서 반경 안의 여행지를 가까움과 그룹 취향으로 정렬.
     * 반경을 안 보내면 그룹 교통수단으로 한 번에 이동하기 적당한 거리({@link TransportRange#nearbyRadiusMeters}).
     */
    public NearbyDestinationsResponse recommendNearby(Long userId, Long groupId, Long destinationId,
                                                      Integer limit, Integer radiusMeters) {
        groupReader.getMemberGroup(groupId, userId);
        Trip trip = groupReader.getActiveStoryTrip(groupId);
        int size = validateLimit(limit);
        if (radiusMeters != null && (radiusMeters < MIN_RADIUS_METERS || radiusMeters > MAX_RADIUS_METERS)) {
            throw new BusinessException(ErrorCode.INVALID_RECOMMENDATION_QUERY);
        }
        // 좌표가 없는 장소(외부 데이터 적재 누락)는 기준으로 쓸 수 없다
        Store base = storeRepository.findById(destinationId)
                .filter(DestinationRecommendationService::hasCoordinates)
                .orElseThrow(() -> new BusinessException(ErrorCode.BASE_DESTINATION_NOT_FOUND));

        GroupTaste taste = GroupTaste.from(preferenceRepository.findAllOfCurrentMembers(trip.getId(), groupId));
        TransportType transport = taste.primaryTransportOr(DEFAULT_NEARBY_TRANSPORT);
        int radius = radiusMeters != null ? radiusMeters
                : Math.max(MIN_RADIUS_METERS, TransportRange.nearbyRadiusMeters(transport, MAX_RADIUS_METERS));

        double latitude = base.getLatitude();
        double longitude = base.getLongitude();
        Bounds bounds = Bounds.around(latitude, longitude, radius);
        List<Store> candidates = storeRepository.findNearAndTypes(
                        bounds.minLatitude(), bounds.maxLatitude(), bounds.minLongitude(), bounds.maxLongitude(),
                        latitude, longitude, bounds.longitudeScale(),
                        DestinationCategories.recommendableTypes(), StoreRepository.regionCandidateLimit()).stream()
                .filter(DestinationRecommendationService::hasCoordinates)
                .filter(store -> !store.getDestinationId().equals(base.getDestinationId()))
                .toList();

        List<NearbyDestinationsResponse.Item> items = groupPlaceFilter.filter(trip, candidates).stream()
                .map(store -> new Nearby(store, store.distanceMetersFrom(latitude, longitude)))
                .filter(nearby -> nearby.distance() <= radius)
                .map(nearby -> nearby.toItem(taste, radius, transport))
                .sorted(Comparator.comparingDouble(NearbyDestinationsResponse.Item::score).reversed()
                        .thenComparingInt(NearbyDestinationsResponse.Item::distanceMeters)
                        .thenComparing(NearbyDestinationsResponse.Item::destinationId))
                .limit(size)
                .toList();

        return new NearbyDestinationsResponse(
                new NearbyDestinationsResponse.BaseDestination(base.getDestinationId(), base.getDestinationName()),
                radius, transport, items);
    }

    private static boolean hasCoordinates(Store store) {
        return store.getLatitude() != null && store.getLongitude() != null;
    }

    /** 중심에서 반경(m)을 덮는 위도·경도 사각 범위와 경도 거리 보정 계수 */
    private record Bounds(double minLatitude, double maxLatitude, double minLongitude, double maxLongitude,
                          double longitudeScale) {
        static Bounds around(double latitude, double longitude, int radiusMeters) {
            double cos = Math.max(Math.cos(Math.toRadians(latitude)), 0.01);
            double latitudeDelta = radiusMeters / METERS_PER_DEGREE;
            double longitudeDelta = radiusMeters / (METERS_PER_DEGREE * cos);
            return new Bounds(latitude - latitudeDelta, latitude + latitudeDelta,
                    longitude - longitudeDelta, longitude + longitudeDelta, cos * cos);
        }
    }

    // ---------------------------------------------------------------- 점수

    /**
     * 리스트 추천 점수: 0.4 + 그 카테고리를 고른 멤버 비율 × 0.4 + 범위 중심에서 가까움(0~0.2).
     */
    private static Scored scoreByTaste(Store store, GroupTaste taste, double distance, int radius) {
        TravelCategory category = DestinationCategories.of(store.getDestinationType());
        double ratio = taste.ratio(category);
        String label = DestinationCategories.label(category);
        String reason;
        if (taste.isEmpty()) {
            reason = "요청한 " + label + " 카테고리와 일치";
        } else if (ratio > 0) {
            reason = label + " 선호와 일치";
        } else {
            reason = "여행 지역의 " + label + " 장소";
        }
        double closeness = Math.max(0, 1 - distance / radius);
        return new Scored(store, category, distance, round2(0.4 + 0.4 * ratio + 0.2 * closeness), reason);
    }

    private static final Comparator<Double> RATING_DESC_NULLS_LAST =
            Comparator.nullsLast(Comparator.<Double>reverseOrder());

    /** 점수 → 중심에서 가까운 순 → 평점 → 장소 ID (점수는 소수 둘째 자리라 가까운 장소끼리 동점이 많다) */
    private static final Comparator<Scored> BY_GROUP_MATCH = Comparator
            .comparingDouble(Scored::score).reversed()
            .thenComparingDouble(Scored::distance)
            .thenComparing(scored -> scored.store().getRating(), RATING_DESC_NULLS_LAST)
            .thenComparing(scored -> scored.store().getDestinationId());

    /** 평점 → 점수 → 중심에서 가까운 순 → 장소 ID */
    private static final Comparator<Scored> BY_RATING = Comparator
            .comparing((Scored scored) -> scored.store().getRating(), RATING_DESC_NULLS_LAST)
            .thenComparing(Comparator.comparingDouble(Scored::score).reversed())
            .thenComparingDouble(Scored::distance)
            .thenComparing(scored -> scored.store().getDestinationId());

    private record Scored(Store store, TravelCategory category, double distance, double score, String reason) {
        DestinationRecommendationsResponse.Item toItem() {
            return new DestinationRecommendationsResponse.Item(store.getDestinationId(), store.getDestinationName(),
                    category, store.getAddress(), store.getLatitude(), store.getLongitude(),
                    (int) Math.round(distance), score, reason);
        }
    }

    /** 2차 추천 점수: 0.4 + 가까움(0~0.4) + 그룹 취향 비율(0~0.2) */
    private record Nearby(Store store, double distance) {
        NearbyDestinationsResponse.Item toItem(GroupTaste taste, int radius, TransportType transport) {
            TravelCategory category = DestinationCategories.of(store.getDestinationType());
            double score = round2(0.4 + 0.4 * (1 - distance / radius) + 0.2 * taste.ratio(category));
            int minutes = TravelTimeEstimator.minutes(transport, TravelTimeEstimator.routeMeters(distance));
            return new NearbyDestinationsResponse.Item(store.getDestinationId(), store.getDestinationName(),
                    category, (int) Math.round(distance), score, minutes);
        }
    }

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }

    // ---------------------------------------------------------------- 요청 값 검사

    private static int validateLimit(Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw new BusinessException(ErrorCode.INVALID_RECOMMENDATION_QUERY);
        }
        return size;
    }

    /** cursor 는 이전 응답의 next_cursor (다음 항목의 위치) */
    private static int parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            int offset = Integer.parseInt(cursor.strip());
            if (offset < 0) {
                throw new BusinessException(ErrorCode.INVALID_RECOMMENDATION_QUERY);
            }
            return offset;
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_RECOMMENDATION_QUERY);
        }
    }

    private static String parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return SORT_GROUP_MATCH;
        }
        String value = sort.strip();
        if (!SORT_GROUP_MATCH.equals(value) && !SORT_RATING.equals(value)) {
            throw new BusinessException(ErrorCode.INVALID_RECOMMENDATION_QUERY);
        }
        return value;
    }

    private static Set<TravelCategory> parseCategories(List<String> values) {
        Set<TravelCategory> categories = EnumSet.noneOf(TravelCategory.class);
        if (values == null) {
            return categories;
        }
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String name = value.strip();
            categories.add(Arrays.stream(TravelCategory.values())
                    .filter(category -> category.name().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_RECOMMENDATION_QUERY)));
        }
        return categories;
    }
}
