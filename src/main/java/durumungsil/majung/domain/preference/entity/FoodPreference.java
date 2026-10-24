package durumungsil.majung.domain.preference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 음식별 선호 점수. 1~5 정수로 받은 값을 그대로 저장/반환한다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FoodPreference {

    public static final int MIN_SCORE = 1;
    public static final int MAX_SCORE = 5;

    @Column(name = "food", nullable = false)
    private String food;

    @Column(name = "score", nullable = false)
    private Integer score;

    public FoodPreference(String food, Integer score) {
        if (food == null || food.isBlank()) {
            throw new IllegalArgumentException("음식 이름은 비어 있을 수 없습니다.");
        }
        if (score == null || score < MIN_SCORE || score > MAX_SCORE) {
            throw new IllegalArgumentException("음식 선호 점수는 1~5 사이 정수여야 합니다.");
        }
        this.food = food;
        this.score = score;
    }
}
