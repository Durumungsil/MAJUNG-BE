package durumungsil.majung.domain.adventure.service;

import durumungsil.majung.domain.adventure.entity.AdventureProcess;
import durumungsil.majung.domain.adventure.entity.Mission;
import durumungsil.majung.domain.adventure.entity.MissionStatus;
import durumungsil.majung.domain.adventure.repository.AdventureProcessRepository;
import durumungsil.majung.domain.adventure.repository.MissionRepository;
import durumungsil.majung.domain.store.entity.Store;
import durumungsil.majung.domain.trip.entity.Trip;
import durumungsil.majung.domain.trip.entity.TripMode;
import durumungsil.majung.domain.trip.repository.TripRepository;
import durumungsil.majung.global.exception.BusinessException;
import durumungsil.majung.global.exception.ErrorCode;
import durumungsil.majung.global.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static durumungsil.majung.domain.adventure.dto.AdventureDtos.*;

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
            throw new BusinessException(ErrorCode.ADVENTURE_ALREADY_EXISTS);
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
                .orElseThrow(() -> new BusinessException(ErrorCode.ADVENTURE_NOT_FOUND));
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
                .orElseThrow(() -> new BusinessException(ErrorCode.CURRENT_MISSION_NOT_FOUND));
        Mission mission = missions.findByProcessIdOrderByMissionOrderAsc(process.getId()).stream()
                .filter(value -> value.getStatus() == MissionStatus.AVAILABLE).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.CURRENT_MISSION_NOT_FOUND));
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
        if (mission.getStartedAt() == null) { throw new BusinessException(ErrorCode.MISSION_NOT_STARTED); }
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
        if (entityManager.find(durumungsil.majung.domain.group.entity.Group.class, groupId,
                jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) == null) {
            throw new BusinessException(ErrorCode.ADVENTURE_GROUP_NOT_FOUND);
        }
        List<Trip> active = trips.findActiveForUpdate(groupId);
        if (active.isEmpty()) { throw new BusinessException(ErrorCode.ADVENTURE_ACTIVE_TRIP_NOT_FOUND); }
        if (active.size() > 1) { throw new BusinessException(ErrorCode.MULTIPLE_ACTIVE_TRIPS); }
        Trip trip = active.getFirst();
        if (trip.getMode() != TripMode.ADVENTURE) { throw new BusinessException(ErrorCode.NOT_ADVENTURE_MODE); }
        return trip;
    }

    private Mission availableMission(Long groupId, Long missionId) {
        Trip trip = activeTrip(groupId);
        Mission mission = missions.findById(missionId)
                .filter(value -> value.getProcess().getTrip().getId().equals(trip.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.MISSION_NOT_FOUND));
        if (!mission.getProcess().getTravelDate().equals(settings.today())) {
            throw new BusinessException(ErrorCode.MISSION_NOT_TODAY);
        }
        if (mission.getStatus() == MissionStatus.COMPLETED) { throw new BusinessException(ErrorCode.MISSION_ALREADY_COMPLETED); }
        if (mission.getStatus() != MissionStatus.AVAILABLE) { throw new BusinessException(ErrorCode.MISSION_LOCKED); }
        return mission;
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
