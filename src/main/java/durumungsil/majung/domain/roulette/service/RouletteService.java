package durumungsil.majung.domain.roulette.service;

import durumungsil.majung.domain.group.service.GroupReader;
import durumungsil.majung.domain.roulette.dto.request.DrawRouletteRequest;
import durumungsil.majung.domain.roulette.dto.response.DrawRouletteResponse;
import durumungsil.majung.domain.roulette.dto.response.MinimumConstraintsResponse;
import durumungsil.majung.domain.roulette.dto.response.RoulettePlaceResponse;
import durumungsil.majung.domain.roulette.dto.response.RouletteResultResponse;
import durumungsil.majung.domain.roulette.entity.RouletteResult;
import durumungsil.majung.domain.roulette.entity.RouletteType;
import durumungsil.majung.domain.roulette.repository.RouletteResultRepository;
import durumungsil.majung.domain.store.entity.Store;
import durumungsil.majung.domain.store.entity.Type;
import durumungsil.majung.domain.store.repository.StoreRepository;
import durumungsil.majung.domain.trip.entity.Trip;
import durumungsil.majung.global.exception.BusinessException;
import durumungsil.majung.global.exception.ErrorCode;
import durumungsil.majung.global.util.RegionAliases;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RouletteService {
    private final GroupReader groupReader;
    private final MinimumConstraintsService constraintsService;
    private final RouletteResultRepository resultRepository;
    private final StoreRepository storeRepository;

    @Transactional
    public DrawRouletteResponse draw(Long userId, Long groupId, RouletteType type, DrawRouletteRequest request) {
        // 그룹 설정·취향 변경과 같은 잠금을 사용해 라운드와 중복 제외를 함께 보장한다.
        groupReader.getMemberGroupForUpdate(groupId, userId);
        Trip trip = groupReader.getActiveTrip(groupId);
        MinimumConstraintsService.validateMode(trip);
        List<String> regions = RegionAliases.of(trip.getRegion());
        if (request.region() != null && !regions.contains(request.region().strip())) {
            throw new BusinessException(ErrorCode.INVALID_ROULETTE_REGION);
        }
        MinimumConstraintsResponse constraints = constraintsService.forTrip(trip);
        List<RouletteResult> previous = resultRepository.findAllByTripIdAndTypeOrderByRoundAsc(trip.getId(), type);
        Set<Long> excludedIds = new HashSet<>(request.excludePlaceIds());
        previous.forEach(result -> excludedIds.add(result.getStore().getDestinationId()));
        excludedIds.add(0L); // SQL IN 목록이 비어 있지 않게 한다. 장소 PK는 양수다.
        Set<String> excludedCategories = new HashSet<>(constraints.excludedCategories());
        excludedCategories.add("");
        Set<Type> types = type == RouletteType.RESTAURANT
                ? Set.of(Type.FOOD) : Set.of(Type.RETAIL, Type.ART_SPORTS);
        String category = request.category() == null ? "" : request.category();
        long count = storeRepository.countRouletteCandidates(regions, types, category, excludedIds, excludedCategories);
        ErrorCode empty = type == RouletteType.RESTAURANT
                ? ErrorCode.NO_ROULETTE_RESTAURANT : ErrorCode.NO_ROULETTE_DESTINATION;
        if (count == 0) throw new BusinessException(empty);
        // 적격 후보 전체에서 균등하게 뽑되 엔티티는 한 곳만 읽는다.
        int offset = Math.toIntExact(ThreadLocalRandom.current().nextLong(count));
        Store selected = storeRepository.findRouletteCandidates(regions, types, category, excludedIds,
                        excludedCategories, PageRequest.of(offset, 1)).stream().findFirst()
                .orElseThrow(() -> new BusinessException(empty));
        int round = previous.stream().mapToInt(RouletteResult::getRound).max().orElse(0) + 1;
        RouletteResult result = resultRepository.saveAndFlush(RouletteResult.builder()
                .trip(trip).type(type).store(selected).round(round).build());
        return new DrawRouletteResponse(result.getId(), type, RoulettePlaceResponse.of(selected),
                type == RouletteType.RESTAURANT ? constraints.allergies() : null, round, result.getDrawnAt());
    }

    public RouletteResultResponse get(Long userId, Long groupId, Long rouletteId) {
        groupReader.getMemberGroup(groupId, userId);
        RouletteResult result = resultRepository.findByIdAndTripGroupId(rouletteId, groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROULETTE_RESULT_NOT_FOUND));
        MinimumConstraintsService.validateMode(result.getTrip());
        // 이전 회차의 저장 결과도 현재 멤버가 다시 열어 길 안내를 이어갈 수 있다.
        return RouletteResultResponse.of(result);
    }
}
