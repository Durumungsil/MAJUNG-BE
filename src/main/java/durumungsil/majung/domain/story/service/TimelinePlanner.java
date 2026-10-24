package durumungsil.majung.domain.story.service;

import durumungsil.majung.domain.preference.entity.TravelCategory;
import durumungsil.majung.domain.store.entity.OpeningHour;
import durumungsil.majung.domain.store.entity.Store;
import durumungsil.majung.global.ai.GeminiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * AI 타임라인 생성.
 * <ol>
 *   <li>Gemini 에게 장소별 방문일·방문 순서·체류 시간·한 줄 메모를 JSON 으로 받는다.</li>
 *   <li>응답을 검증한다: 모든 장소가 정확히 한 번, 여행 기간 안의 날짜, 방문일이 정해진 장소는 그 날.</li>
 *   <li>시각 계산은 {@link TimelineScheduler} 가 한다 (이동 시간·영업 시간·하루 일정 시간 검사).</li>
 * </ol>
 * API 키가 없거나, 호출이 실패하거나, 응답이 검증을 통과하지 못하거나, 동선이 규칙 기반보다 나쁘면
 * 규칙 기반 배치({@link #planWithRules})로 대신한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimelinePlanner {

    public static final String GENERATED_BY_AI = "AI";
    public static final String GENERATED_BY_RULE = "RULE";

    static final int MIN_STAY_MINUTES = 30;
    static final int MAX_STAY_MINUTES = 240;
    static final int MAX_NOTE_LENGTH = 100;

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    /** @param generatedBy AI 또는 RULE */
    public record Result(List<TimelineScheduler.PlannedItem> items, String generatedBy) {
    }

    /**
     * @param categoryVotes 카테고리별로 고른 멤버 수 (AI 에게 그룹 취향으로 전달)
     * @return 여행 기간 안에 배치할 수 없으면 empty
     */
    public Optional<Result> plan(List<TimelineScheduler.Stop> stops, TimelineScheduler.Settings settings,
                                 Map<TravelCategory, Integer> categoryVotes) {
        Optional<List<TimelineScheduler.PlannedItem>> rulePlan = planWithRules(stops, settings);
        if (geminiClient.isEnabled()) {
            Optional<List<TimelineScheduler.PlannedItem>> aiPlan = planWithAi(stops, settings, categoryVotes);
            if (aiPlan.isPresent()) {
                if (rulePlan.isEmpty() || !hasWorseRoute(aiPlan.get(), rulePlan.get(), settings)) {
                    return Optional.of(new Result(aiPlan.get(), GENERATED_BY_AI));
                }
                log.warn("AI 타임라인 동선이 규칙 기반보다 길어 규칙 기반으로 생성합니다. (AI {}, 규칙 {})",
                        RouteSummary.of(aiPlan.get(), settings), RouteSummary.of(rulePlan.get(), settings));
            }
        }
        return rulePlan.map(items -> new Result(items, GENERATED_BY_RULE));
    }

    /**
     * 규칙 기반 배치. 동선을 최적화한 순서로 먼 이동은 다음 날로 나눠 보고, 안 되면 조건을 하나씩 풀어 본다.
     * <ol>
     *   <li>최적화한 순서 + 먼 이동 나누기</li>
     *   <li>최적화한 순서</li>
     *   <li>받은 순서 그대로</li>
     * </ol>
     */
    static Optional<List<TimelineScheduler.PlannedItem>> planWithRules(List<TimelineScheduler.Stop> stops,
                                                                      TimelineScheduler.Settings settings) {
        List<TimelineScheduler.Stop> optimized = RouteOptimizer.optimize(stops, settings.startDate());
        return TimelineScheduler.schedule(optimized, settings, true)
                .or(() -> TimelineScheduler.schedule(optimized, settings, false))
                .or(() -> TimelineScheduler.schedule(stops, settings, false));
    }

    /**
     * AI 동선이 규칙 기반보다 나쁜지: 먼 이동이 더 많거나, 같은 날 이동 거리 합이 30% + 1km 를 넘게 길면 나쁘다고 본다.
     */
    static boolean hasWorseRoute(List<TimelineScheduler.PlannedItem> ai, List<TimelineScheduler.PlannedItem> rule,
                                 TimelineScheduler.Settings settings) {
        RouteSummary aiRoute = RouteSummary.of(ai, settings);
        RouteSummary ruleRoute = RouteSummary.of(rule, settings);
        return aiRoute.longMoves() > ruleRoute.longMoves()
                || aiRoute.totalMeters() > ruleRoute.totalMeters() * 1.3 + 1_000;
    }

    /** 배치 결과의 이동 요약: 이동 거리 합(경로 m)과 교통수단 적정 거리를 넘는 이동 수 */
    record RouteSummary(long totalMeters, int longMoves) {
        static RouteSummary of(List<TimelineScheduler.PlannedItem> items, TimelineScheduler.Settings settings) {
            long total = 0;
            int longMoves = 0;
            for (TimelineScheduler.PlannedItem item : items) {
                if (item.distanceMeters() != null) {
                    total += item.distanceMeters();
                    if (TransportRange.isLongDistance(settings.transportType(), item.distanceMeters())) {
                        longMoves++;
                    }
                }
            }
            return new RouteSummary(total, longMoves);
        }
    }

    private Optional<List<TimelineScheduler.PlannedItem>> planWithAi(List<TimelineScheduler.Stop> stops,
                                                                    TimelineScheduler.Settings settings,
                                                                    Map<TravelCategory, Integer> categoryVotes) {
        try {
            Optional<String> answer = geminiClient.generateJson(buildPrompt(stops, settings, categoryVotes),
                    RESPONSE_SCHEMA);
            if (answer.isEmpty()) {
                log.warn("AI 타임라인 응답이 없어 규칙 기반으로 생성합니다.");
                return Optional.empty();
            }
            Optional<List<TimelineScheduler.Stop>> ordered = parse(answer.get(), stops, settings);
            if (ordered.isEmpty()) {
                log.warn("AI 타임라인 응답이 조건에 맞지 않아 규칙 기반으로 생성합니다: {}", abbreviate(answer.get()));
                return Optional.empty();
            }
            Optional<List<TimelineScheduler.PlannedItem>> planned = TimelineScheduler.schedule(ordered.get(), settings);
            if (planned.isEmpty()) {
                log.warn("AI 타임라인이 영업 시간·이동 시간·하루 일정과 맞지 않아 규칙 기반으로 생성합니다.");
            }
            return planned;
        } catch (RuntimeException e) {
            log.warn("AI 타임라인 생성 중 오류가 나서 규칙 기반으로 생성합니다: {}", e.toString());
            return Optional.empty();
        }
    }

    // ---------------------------------------------------------------- 프롬프트

    private String buildPrompt(List<TimelineScheduler.Stop> stops, TimelineScheduler.Settings settings,
                               Map<TravelCategory, Integer> categoryVotes) {
        List<Map<String, Object>> places = new ArrayList<>();
        for (int i = 0; i < stops.size(); i++) {
            TimelineScheduler.Stop stop = stops.get(i);
            Store store = stop.store();
            Map<String, Object> place = new LinkedHashMap<>();
            place.put("stop_id", i);
            place.put("name", store.getDestinationName());
            place.put("category", String.valueOf(DestinationCategories.of(store.getDestinationType())));
            place.put("latitude", store.getLatitude());
            place.put("longitude", store.getLongitude());
            place.put("fixed_date", stop.fixedDate() == null ? null : stop.fixedDate().toString());
            place.put("default_stay_minutes", stop.stayOrDefault());
            List<Map<String, String>> hours = new ArrayList<>();
            for (OpeningHour hour : store.getOpeningHours()) {
                hours.add(Map.of("day_of_week", hour.getDayOfWeek().name(),
                        "open", hour.getOpenTime().toString(),
                        "close", hour.getCloseTime().toString()));
            }
            place.put("opening_hours", hours);
            List<Double> distances = new ArrayList<>();
            for (TimelineScheduler.Stop other : stops) {
                int route = TravelTimeEstimator.routeMeters(RouteOptimizer.meters(store, other.store()));
                distances.add(Math.round(route / 100.0) / 10.0);
            }
            place.put("distances_km", distances);
            places.add(place);
        }

        Map<String, Integer> preferences = new LinkedHashMap<>();
        categoryVotes.forEach((category, votes) -> preferences.put(category.name(), votes));

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("start_date", settings.startDate().toString());
        input.put("end_date", settings.endDate().toString());
        input.put("day_start_time", settings.dayStartTime().toString());
        input.put("day_end_time", settings.dayEndTime().toString());
        input.put("transport_type", String.valueOf(settings.transportType()));
        input.put("max_move_km", TransportRange.maxMoveMeters(settings.transportType()) / 1000.0);
        input.put("group_category_votes", preferences);
        input.put("places", places);

        return """
                너는 그룹 여행 일정을 짜는 여행 플래너야.
                아래 입력의 장소들을 여행 기간의 날짜에 나눠 담고, 날짜마다 방문 순서를 정해.

                규칙:
                1. places 의 모든 stop_id 를 정확히 한 번씩 써. 없는 stop_id 를 만들거나 빼먹으면 안 돼.
                2. fixed_date 가 있는 장소는 반드시 그 날짜에 넣어.
                3. 날짜는 start_date 부터 end_date 사이(yyyy-MM-dd)여야 해.
                4. 이동 거리가 가장 중요해. places[i].distances_km[j] 는 stop_id i 에서 j 까지 이동 거리(km)야.
                   같은 날 이어서 방문하는 두 장소 사이 거리는 max_move_km(교통수단 transport_type 으로 무리 없이 갈 수 있는 거리) 이하여야 해.
                   그보다 먼 장소는 다른 날로 나눠. 하루 안에서는 이동 거리 합이 가장 짧은 순서로 정해.
                5. opening_hours 가 있으면 그 시간에 방문할 수 있는 순서로 정해. 비어 있으면 언제든 방문 가능하다고 봐.
                6. 하루 일정은 day_start_time 부터 day_end_time 까지야. 체류 시간과 이동 시간을 더해 이 안에 들어가게 날짜를 나눠.
                7. stay_minutes 는 30~240 사이로 정해. 모르면 default_stay_minutes 를 써.
                8. note 에는 그 장소에서 할 일을 한국어 한 문장(40자 이내)으로 써. 방문 시각은 서버가 정하니
                   아침·점심·저녁 같은 시간대나 시각은 쓰지 마.
                9. group_category_votes 는 카테고리별로 그 카테고리를 좋아하는 멤버 수야. 많이 고른 카테고리를 여유 있게 배치해.

                방문 시각은 서버가 계산하니 날짜·순서·체류 시간·메모만 JSON 으로 답해.

                입력:
                """ + objectMapper.writeValueAsString(input);
    }

    /** Gemini 구조화 출력 스키마. 키 이름은 그대로 전송된다 */
    static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "days", Map.of(
                            "type", "array",
                            "items", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "date", Map.of("type", "string", "format", "date"),
                                            "visits", Map.of(
                                                    "type", "array",
                                                    "items", Map.of(
                                                            "type", "object",
                                                            "properties", Map.of(
                                                                    "stop_id", Map.of("type", "integer"),
                                                                    "stay_minutes", Map.of("type", "integer",
                                                                            "minimum", MIN_STAY_MINUTES,
                                                                            "maximum", MAX_STAY_MINUTES),
                                                                    "note", Map.of("type", "string")),
                                                            "required", List.of("stop_id", "stay_minutes")))),
                                    "required", List.of("date", "visits")))),
            "required", List.of("days"));

    // ---------------------------------------------------------------- 응답 검증

    /**
     * AI 응답을 배치 순서대로 정렬한 Stop 목록으로 바꾼다.
     * 날짜는 AI 가 정한 날로 고정하고, 체류 시간은 30~240분으로 맞춘다. 조건에 맞지 않으면 empty.
     */
    Optional<List<TimelineScheduler.Stop>> parse(String answer, List<TimelineScheduler.Stop> stops,
                                                 TimelineScheduler.Settings settings) {
        JsonNode days = objectMapper.readTree(stripCodeFence(answer)).path("days");
        if (!days.isArray()) {
            return Optional.empty();
        }
        boolean[] used = new boolean[stops.size()];
        int usedCount = 0;
        TreeMap<LocalDate, List<TimelineScheduler.Stop>> byDate = new TreeMap<>();

        for (JsonNode day : days) {
            LocalDate date = parseDate(day.path("date"));
            if (date == null || date.isBefore(settings.startDate()) || date.isAfter(settings.endDate())
                    || !day.path("visits").isArray()) {
                return Optional.empty();
            }
            List<TimelineScheduler.Stop> dayStops = byDate.computeIfAbsent(date, key -> new ArrayList<>());
            for (JsonNode visit : day.path("visits")) {
                JsonNode stopId = visit.path("stop_id");
                if (!stopId.isIntegralNumber()) {
                    return Optional.empty();
                }
                int index = stopId.asInt();
                if (index < 0 || index >= stops.size() || used[index]) {
                    return Optional.empty();
                }
                TimelineScheduler.Stop original = stops.get(index);
                if (original.fixedDate() != null && !original.fixedDate().equals(date)) {
                    return Optional.empty();
                }
                used[index] = true;
                usedCount++;
                dayStops.add(new TimelineScheduler.Stop(original.store(), date,
                        stayMinutes(visit.path("stay_minutes"), original), note(visit.path("note"))));
            }
        }
        if (usedCount != stops.size()) {
            return Optional.empty();
        }
        List<TimelineScheduler.Stop> ordered = new ArrayList<>();
        byDate.values().forEach(ordered::addAll);
        return Optional.of(ordered);
    }

    private static LocalDate parseDate(JsonNode node) {
        if (!node.isString()) {
            return null;
        }
        try {
            return LocalDate.parse(node.asString().strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static int stayMinutes(JsonNode node, TimelineScheduler.Stop original) {
        if (!node.isIntegralNumber()) {
            return original.stayOrDefault();
        }
        return Math.clamp(node.asInt(), MIN_STAY_MINUTES, MAX_STAY_MINUTES);
    }

    private static String note(JsonNode node) {
        if (!node.isString() || node.asString().isBlank()) {
            return null;
        }
        String value = node.asString().strip();
        return value.length() <= MAX_NOTE_LENGTH ? value : value.substring(0, MAX_NOTE_LENGTH);
    }

    /** 스키마를 무시하고 ```json ... ``` 으로 감싸서 답하는 경우 대비 */
    private static String stripCodeFence(String answer) {
        String text = answer.strip();
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            int closing = text.lastIndexOf("```");
            if (firstLineEnd > 0 && closing > firstLineEnd) {
                return text.substring(firstLineEnd + 1, closing);
            }
        }
        return text;
    }

    private static String abbreviate(String value) {
        return value.length() <= 300 ? value : value.substring(0, 300) + "...";
    }
}
