package durumungsil.majung.domain.adventure;

/** 모험 커리큘럼(하루치) 상태 */
public enum AdventureStatus {
    /** 생성됨, 아직 첫 미션 시작 전 */
    READY,
    /** 미션 진행 중 */
    IN_PROGRESS,
    /** 그날 미션을 모두 완료 */
    COMPLETED
}
