package durumungsil.majung.domain.preference.entity;

/**
 * 여행 스타일. 설문 선택지와 1:1 로 맞춘다 (선언 순서 = 설문 순서, 취향 통합 동률 정렬에도 쓴다).
 */
public enum TravelCategory {
    HEALING,    // 힐링
    FESTIVAL,   // 축제
    SHOPPING,   // 쇼핑
    CULTURE,    // 문화관광
    ACTIVITY,   // 액티비티
    FOOD,       // 맛집 탐방
    NATURE,     // 자연친화적
    CAFE,       // 카페·디저트
    SIGHTSEEING // 관광명소
}
