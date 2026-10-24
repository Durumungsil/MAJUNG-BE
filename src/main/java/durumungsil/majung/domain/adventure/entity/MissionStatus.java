package durumungsil.majung.domain.adventure.entity;

/**
 * 미션 상태.
 * 위치 인증 실패(FAILED)는 상태가 아니라 인증 결과이므로 저장하지 않는다(미션은 AVAILABLE 로 남아 재시도 가능).
 */
public enum MissionStatus {
    /** 아직 차례가 오지 않음 (이전 미션 완료 전) */
    LOCKED,
    /** 진행 가능 (현재 미션) */
    AVAILABLE,
    /** 완료 */
    COMPLETED
}
