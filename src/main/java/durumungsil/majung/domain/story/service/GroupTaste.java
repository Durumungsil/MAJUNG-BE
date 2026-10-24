package durumungsil.majung.domain.story.service;

import durumungsil.majung.domain.preference.entity.Preference;
import durumungsil.majung.domain.preference.entity.TransportType;
import durumungsil.majung.domain.preference.entity.TravelCategory;
import durumungsil.majung.global.util.EnumJsonArrays;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * 스토리모드 추천·타임라인에서 쓰는 그룹 취향 집계.
 * 취향 통합(PreferenceAggregator)은 최빈값만 내려주므로, 카테고리별로 몇 명이 골랐는지는 여기서 센다.
 *
 * @param respondentCount   취향을 입력한 현재 멤버 수
 * @param categoryVotes     카테고리별로 고른 멤버 수
 * @param primaryTransport  가장 많이 고른 교통수단 (동률이면 enum 선언 순서상 앞의 것). 없으면 null
 */
public record GroupTaste(int respondentCount, Map<TravelCategory, Integer> categoryVotes,
                         TransportType primaryTransport) {

    public static GroupTaste from(List<Preference> preferences) {
        Map<TravelCategory, Integer> votes = new EnumMap<>(TravelCategory.class);
        Map<TransportType, Integer> transports = new EnumMap<>(TransportType.class);
        for (Preference preference : preferences) {
            List<TravelCategory> categories = EnumJsonArrays.fromJson(preference.getTravelCategories(), TravelCategory.class);
            if (!categories.isEmpty()) {
                EnumSet.copyOf(categories).forEach(category -> votes.merge(category, 1, Integer::sum));
            }
            preference.getTransportTypes().forEach(type -> transports.merge(type, 1, Integer::sum));
        }

        TransportType primary = null;
        int best = 0;
        for (Map.Entry<TransportType, Integer> entry : transports.entrySet()) { // EnumMap 은 선언 순서로 순회
            if (entry.getValue() > best) {
                best = entry.getValue();
                primary = entry.getKey();
            }
        }
        return new GroupTaste(preferences.size(), Collections.unmodifiableMap(votes), primary);
    }

    public boolean isEmpty() {
        return respondentCount == 0;
    }

    /** 해당 카테고리를 고른 멤버 비율 (0~1) */
    public double ratio(TravelCategory category) {
        if (respondentCount == 0 || category == null) {
            return 0;
        }
        return (double) categoryVotes.getOrDefault(category, 0) / respondentCount;
    }

    public TransportType primaryTransportOr(TransportType fallback) {
        return primaryTransport != null ? primaryTransport : fallback;
    }
}
