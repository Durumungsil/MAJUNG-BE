package durumungsil.majung.domain.adventure;

import durumungsil.majung.domain.store.Store;
import durumungsil.majung.domain.trip.Trip;
import durumungsil.majung.domain.trip.TripMode;
import durumungsil.majung.domain.trip.TripRepository;
import durumungsil.majung.global.api.ApiException;
import durumungsil.majung.global.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static durumungsil.majung.domain.adventure.AdventureDtos.*;

@Service
@RequiredArgsConstructor
@Transactional
public class AdventureService {
    private final jakarta.persistence.EntityManager entityManager;
    private final TripRepository trips;
    private final AdventureProcessRepository processes;
    private final MissionRepository missions;
    private final AdventureDestinationSelector selector;
    private final AdventureSettings settings;

    public Created create(Long groupId, CreateRequest request) {
        if (request.travelDate() == null || request.missionCount() == null
                || request.missionCount() < 1 || request.missionCount() > settings.maxMissionCount()) {
            throw new IllegalArgumentException("여행 날짜와 허용 범위의 미션 수가 필요합니다.");
        }
        Trip trip = activeTrip(groupId);
        if (!trip.includes(request.travelDate())) {
            throw new IllegalArgumentException("모험 날짜는 여행 기간 안이어야 합니다.");
        }
        if (processes.existsByTripIdAndTravelDate(trip.getId(), request.travelDate())) {
            throw ApiException.conflict("해당 날짜의 커리큘럼이 이미 있습니다.");
        }
        List<Store> destinations = selector.select(trip, request.missionCount());
        AdventureProcess process = processes.saveAndFlush(AdventureProcess.builder().trip(trip)
                .travelDate(request.travelDate()).missionCount(destinations.size())
                .destinationCount(destinations.size()).build());
        List<Mission> generated = new ArrayList<>();
        for (int i = 0; i < destinations.size(); i++) {
            generated.add(Mission.builder().process(process).store(destinations.get(i)).missionOrder(i + 1)
                    .status(i == 0 ? MissionStatus.AVAILABLE : MissionStatus.LOCKED)
                    .title("다음 장소를 찾아라").objective("힌트를 참고해 목적지에 도착하세요.").build());
        }
        missions.saveAll(generated);
        return new Created(process.getId(), groupId, process.getStatus(), destinations.size(), destinations.size(),
                generated.getFirst().getId(), process.getCreatedAt());
    }

    public ProcessView progress(Long groupId, Long processId) {
        Trip trip = activeTrip(groupId);
        AdventureProcess process = processes.findById(processId)
                .filter(value -> value.getTrip().getId().equals(trip.getId()))
                .orElseThrow(() -> ApiException.notFound("커리큘럼을 찾을 수 없습니다."));
        List<Mission> all = missions.findByProcessIdOrderByMissionOrderAsc(processId);
        int completed = (int) all.stream().filter(value -> value.getStatus() == MissionStatus.COMPLETED).count();
        Long current = all.stream().filter(value -> value.getStatus() == MissionStatus.AVAILABLE)
                .map(Mission::getId).findFirst().orElse(null);
        return new ProcessView(processId, process.getStatus(), new Progress(completed, all.size()), current,
                all.stream().map(value -> new MissionProgress(value.getId(), value.getMissionOrder(),
                        value.getStatus(), instant(value.getCompletedAt()))).toList());
    }

    public CurrentMission current(Long groupId) {
        Trip trip = activeTrip(groupId);
        AdventureProcess process = processes.findByTripIdAndTravelDate(trip.getId(), settings.today())
                .orElseThrow(() -> ApiException.notFound("진행할 수 있는 현재 미션이 없습니다."));
        Mission mission = missions.findByProcessIdOrderByMissionOrderAsc(process.getId()).stream()
                .filter(value -> value.getStatus() == MissionStatus.AVAILABLE).findFirst()
                .orElseThrow(() -> ApiException.notFound("진행할 수 있는 현재 미션이 없습니다."));
        return new CurrentMission(mission.getId(), mission.getMissionOrder(), mission.getStatus(), mission.getTitle(),
                mission.getObjective(), mission.getHintCount(), instant(mission.getStartedAt()));
    }

    public Started start(Long groupId, Long missionId, StartRequest request) {
        GeoUtils.validate(request.latitude(), request.longitude());
        Mission mission = availableMission(groupId, missionId);
        mission.start(settings.nowUtc(), request.latitude(), request.longitude());
        mission.getProcess().start();
        return new Started(missionId, instant(mission.getStartedAt()));
    }

    public Hint hint(Long groupId, Long missionId) {
        Mission mission = availableMission(groupId, missionId);
        mission.useHint();
        return new Hint(missionId, mission.getStore().getLatitude(), mission.getStore().getLongitude(),
                settings.hintViewSeconds(), mission.getHintCount());
    }

    public Completed complete(Long groupId, Long missionId, CompleteRequest request) {
        GeoUtils.validate(request.latitude(), request.longitude());
        Mission mission = availableMission(groupId, missionId);
        if (mission.getStartedAt() == null) { throw ApiException.conflict("시작하지 않은 미션입니다."); }
        LocalDateTime now = settings.nowUtc();
        if (request.completedAt() != null && (request.completedAt().isAfter(now.toInstant(ZoneOffset.UTC))
                || request.completedAt().isBefore(instant(mission.getStartedAt())))) {
            throw new IllegalArgumentException("완료 시각은 시작 시각과 현재 시각 사이여야 합니다.");
        }
        if (mission.getStore().distanceMetersFrom(request.latitude(), request.longitude()) > settings.arrivalRadiusMeters()) {
            throw new IllegalArgumentException("현재 위치가 미션 목적지와 충분히 가깝지 않습니다.");
        }
        // 랭크와 완료 시각은 클라이언트가 바꿀 수 없도록 서버 시각으로 계산한다.
        mission.complete(now);
        List<Mission> all = missions.findByProcessIdOrderByMissionOrderAsc(mission.getProcess().getId());
        Mission next = all.stream().filter(value -> value.getMissionOrder() > mission.getMissionOrder())
                .findFirst().orElse(null);
        if (next == null) { mission.getProcess().finish(); }
        else { next.unlock(); }
        return new Completed(missionId, mission.getStatus(), next == null ? null : next.getId(),
                instant(mission.getCompletedAt()));
    }

    private Trip activeTrip(Long groupId) {
        // 멤버·취향·여행 설정 변경과도 같은 그룹 → 여행 순서로 잠근다.
        if (entityManager.find(durumungsil.majung.domain.group.Group.class, groupId,
                jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) == null) {
            throw ApiException.notFound("그룹을 찾을 수 없습니다.");
        }
        List<Trip> active = trips.findActiveForUpdate(groupId);
        if (active.isEmpty()) { throw ApiException.notFound("진행 중인 여행 회차를 찾을 수 없습니다."); }
        if (active.size() > 1) { throw ApiException.conflict("진행 중인 여행 회차가 여러 개입니다."); }
        Trip trip = active.getFirst();
        if (trip.getMode() != TripMode.ADVENTURE) { throw ApiException.conflict("모험모드 여행이 아닙니다."); }
        return trip;
    }

    private Mission availableMission(Long groupId, Long missionId) {
        Trip trip = activeTrip(groupId);
        Mission mission = missions.findById(missionId)
                .filter(value -> value.getProcess().getTrip().getId().equals(trip.getId()))
                .orElseThrow(() -> ApiException.notFound("미션을 찾을 수 없습니다."));
        if (!mission.getProcess().getTravelDate().equals(settings.today())) {
            throw ApiException.conflict("오늘 진행할 수 있는 미션이 아닙니다.");
        }
        if (mission.getStatus() == MissionStatus.COMPLETED) { throw ApiException.conflict("이미 완료된 미션입니다."); }
        if (mission.getStatus() != MissionStatus.AVAILABLE) { throw ApiException.conflict("아직 진행할 수 없는 미션입니다."); }
        return mission;
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
