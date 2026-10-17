package durumungsil.majung.domain.adventure;

import java.time.Duration;

/**
 * 미션 클리어 랭크.
 * 100점에서 힌트 사용 횟수와 예상 시간 초과 정도만큼 감점한 점수로 등급을 정한다.
 */
public enum Rank {
    SS(95),
    S(85),
    A(75),
    B(60),
    C(45),
    D(30),
    E(0);

    /** 힌트 1회당 감점 */
    static final int HINT_PENALTY = 10;
    /** 예상 시간 대비 초과 비율 1.0(=2배 걸림)당 감점 */
    static final int OVERTIME_PENALTY_PER_RATIO = 50;
    /** 시간 감점 상한 */
    static final int MAX_OVERTIME_PENALTY = 50;
    /** 걷는 속도(m/분) - 기준 시간 계산용 */
    static final double WALKING_METERS_PER_MINUTE = 60.0;
    /** 실제 길은 직선보다 구불구불하므로 직선거리에 곱하는 보정 계수 */
    static final double ROUTE_DETOUR_FACTOR = 1.3;
    /** 아주 가까운 미션도 최소한 이만큼은 여유를 준다(분) */
    static final int MIN_EXPECTED_MINUTES = 5;

    private final int minScore;

    Rank(int minScore) {
        this.minScore = minScore;
    }

    /**
     * @param hintCount        사용한 힌트 횟수
     * @param elapsed          미션 시작~완료까지 걸린 시간
     * @param estimatedMinutes 기준 소요 시간(분). 모르면 null → 시간 감점 없음
     */
    public static Rank of(int hintCount, Duration elapsed, Integer estimatedMinutes) {
        int score = score(hintCount, elapsed, estimatedMinutes);
        for (Rank rank : values()) {
            if (score >= rank.minScore) {
                return rank;
            }
        }
        return E;
    }

    static int score(int hintCount, Duration elapsed, Integer estimatedMinutes) {
        int hintPenalty = Math.max(hintCount, 0) * HINT_PENALTY;
        int overtimePenalty = 0;
        if (elapsed != null && estimatedMinutes != null && estimatedMinutes > 0) {
            double ratio = elapsed.toSeconds() / (estimatedMinutes * 60.0);
            if (ratio > 1.0) {
                overtimePenalty = (int) Math.min((ratio - 1.0) * OVERTIME_PENALTY_PER_RATIO, MAX_OVERTIME_PENALTY);
            }
        }
        return 100 - hintPenalty - overtimePenalty;
    }

    /** 직선거리(m)로 걸어서 가는 기준 시간(분)을 계산 */
    public static int expectedWalkingMinutes(double straightDistanceMeters) {
        int minutes = (int) Math.ceil(straightDistanceMeters * ROUTE_DETOUR_FACTOR / WALKING_METERS_PER_MINUTE);
        return Math.max(minutes, MIN_EXPECTED_MINUTES);
    }
}
