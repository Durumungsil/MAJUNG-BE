package durumungsil.majung.domain.trip.entity;

/** 여행 회차 상태 */
public enum TripStatus {
    /** 계획 중 (모드 선택, 취향 입력, 일정 준비) */
    PLANNING,
    /** 여행 진행 중 */
    IN_PROGRESS,
    /** 여행 종료 */
    COMPLETED
}
