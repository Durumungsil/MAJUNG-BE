package durumungsil.majung;

import durumungsil.majung.domain.story.entity.ItineraryItemType;
import durumungsil.majung.domain.preference.entity.TransportType;
import durumungsil.majung.domain.preference.entity.TravelCategory;
import durumungsil.majung.domain.place.PlanningPlace;
import durumungsil.majung.global.ai.GeminiClient;
import durumungsil.majung.domain.story.service.TimelinePlanner;
import durumungsil.majung.domain.story.service.TimelineScheduler;
import durumungsil.majung.domain.story.service.TransportRange;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** AI 응답을 가짜 Gemini 로 흉내 내서 검증·대체(규칙 기반) 동작을 확인한다 */
class TimelinePlannerTests {

    static final LocalDate DAY1 = LocalDate.of(2026, 10, 3);
    static final TimelineScheduler.Settings SETTINGS = new TimelineScheduler.Settings(DAY1, DAY1.plusDays(1),
            TransportType.WALK, LocalTime.of(9, 0), LocalTime.of(21, 0));

    final PlanningPlace museum = place("박물관", TravelCategory.CULTURE, 35.100);
    final PlanningPlace market = place("시장", TravelCategory.SHOPPING, 35.101);
    final PlanningPlace restaurant = place("식당", TravelCategory.FOOD, 35.102);
    final List<TimelineScheduler.Stop> stops = List.of(
            new TimelineScheduler.Stop(museum, null),
            new TimelineScheduler.Stop(market, null),
            new TimelineScheduler.Stop(restaurant, null));

    static PlanningPlace place(String name, TravelCategory type, double latitude) {
        return new PlanningPlace(name,latitude,129.0,type);
    }

    /** 고정된 답을 돌려주는 가짜 Gemini. 호출 횟수를 센다 */
    static final class FakeGemini implements GeminiClient {
        final boolean enabled;
        final String answer;
        final AtomicInteger calls = new AtomicInteger();
        String lastPrompt;

        FakeGemini(boolean enabled, String answer) {
            this.enabled = enabled;
            this.answer = answer;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public Optional<String> generateJson(String prompt, Map<String, Object> responseSchema) {
            lastPrompt=prompt;
            calls.incrementAndGet();
            return Optional.ofNullable(answer);
        }
    }

    TimelinePlanner.Result plan(FakeGemini gemini, List<TimelineScheduler.Stop> input) {
        return new TimelinePlanner(gemini, JsonMapper.builder().build())
                .plan(input, SETTINGS, Map.of(TravelCategory.CULTURE, 2))
                .orElseThrow();
    }

    static List<String> visitNames(TimelinePlanner.Result result) {
        return result.items().stream()
                .filter(item -> item.type() == ItineraryItemType.VISIT)
                .map(item -> item.place().id())
                .toList();
    }

    @Test
    void aiOrderStayAndNoteAreUsed() {
        FakeGemini gemini = new FakeGemini(true, """
                {"days":[
                  {"date":"2026-10-03","visits":[
                    {"stop_id":2,"stay_minutes":45,"note":"점심 먹기"},
                    {"stop_id":0,"stay_minutes":120,"note":"전시 관람"}]},
                  {"date":"2026-10-04","visits":[{"stop_id":1,"stay_minutes":60}]}]}
                """);

        TimelinePlanner.Result result = plan(gemini, stops);

        assertFalse(gemini.lastPrompt.contains("\"name\""));
        assertFalse(gemini.lastPrompt.contains("\"latitude\""));
        assertEquals(TimelinePlanner.GENERATED_BY_AI, result.generatedBy());
        assertEquals(List.of("식당", "박물관", "시장"), visitNames(result));
        TimelineScheduler.PlannedItem first = result.items().get(0);
        assertEquals(LocalTime.of(9, 0), first.startAt());
        assertEquals(LocalTime.of(9, 45), first.endAt());
        assertEquals("점심 먹기", first.memo());
        TimelineScheduler.PlannedItem last = result.items().get(result.items().size() - 1);
        assertEquals(DAY1.plusDays(1), last.date());
        assertNull(last.memo());
    }

    @Test
    void codeFenceAndOutOfRangeStayAreTolerated() {
        FakeGemini gemini = new FakeGemini(true, """
                ```json
                {"days":[{"date":"2026-10-03","visits":[
                  {"stop_id":1,"stay_minutes":5},{"stop_id":0,"stay_minutes":999},{"stop_id":2}]}]}
                ```
                """);

        TimelinePlanner.Result result = plan(gemini, stops);

        assertEquals(TimelinePlanner.GENERATED_BY_AI, result.generatedBy());
        List<Integer> stays = result.items().stream()
                .filter(item -> item.type() == ItineraryItemType.VISIT)
                .map(TimelineScheduler.PlannedItem::stayMinutes)
                .toList();
        assertEquals(List.of(30, 240, 60), stays); // 30~240 으로 맞추고, 없으면 업종 기본값(음식 60)
    }

    @Test
    void missingStopFallsBackToRules() {
        FakeGemini gemini = new FakeGemini(true, """
                {"days":[{"date":"2026-10-03","visits":[{"stop_id":0,"stay_minutes":60}]}]}
                """);

        TimelinePlanner.Result result = plan(gemini, stops);

        assertEquals(TimelinePlanner.GENERATED_BY_RULE, result.generatedBy());
        assertEquals(List.of("박물관", "시장", "식당"), visitNames(result));
    }

    @Test
    void duplicatedOrUnknownStopFallsBackToRules() {
        String duplicated = """
                {"days":[{"date":"2026-10-03","visits":[
                  {"stop_id":0,"stay_minutes":60},{"stop_id":0,"stay_minutes":60},{"stop_id":1,"stay_minutes":60}]}]}
                """;
        String unknown = """
                {"days":[{"date":"2026-10-03","visits":[
                  {"stop_id":0,"stay_minutes":60},{"stop_id":1,"stay_minutes":60},{"stop_id":7,"stay_minutes":60}]}]}
                """;

        assertEquals(TimelinePlanner.GENERATED_BY_RULE, plan(new FakeGemini(true, duplicated), stops).generatedBy());
        assertEquals(TimelinePlanner.GENERATED_BY_RULE, plan(new FakeGemini(true, unknown), stops).generatedBy());
    }

    @Test
    void dateOutsideTripOrIgnoringFixedDateFallsBackToRules() {
        String outside = """
                {"days":[{"date":"2026-12-25","visits":[
                  {"stop_id":0,"stay_minutes":60},{"stop_id":1,"stay_minutes":60},{"stop_id":2,"stay_minutes":60}]}]}
                """;
        assertEquals(TimelinePlanner.GENERATED_BY_RULE, plan(new FakeGemini(true, outside), stops).generatedBy());

        List<TimelineScheduler.Stop> fixed = List.of(new TimelineScheduler.Stop(museum, DAY1.plusDays(1)));
        String movedFixed = """
                {"days":[{"date":"2026-10-03","visits":[{"stop_id":0,"stay_minutes":60}]}]}
                """;
        TimelinePlanner.Result result = plan(new FakeGemini(true, movedFixed), fixed);
        assertEquals(TimelinePlanner.GENERATED_BY_RULE, result.generatedBy());
        assertEquals(DAY1.plusDays(1), result.items().get(0).date());
    }

    @Test
    void aiPlanThatDoesNotFitTheDayFallsBackToRules() {
        // 하루(09~21시)에 240분씩 세 곳 + 이동 = 들어가지 않음
        FakeGemini gemini = new FakeGemini(true, """
                {"days":[{"date":"2026-10-03","visits":[
                  {"stop_id":0,"stay_minutes":240},{"stop_id":1,"stay_minutes":240},{"stop_id":2,"stay_minutes":240}]}]}
                """);

        assertEquals(TimelinePlanner.GENERATED_BY_RULE, plan(gemini, stops).generatedBy());
    }

    @Test
    void brokenOrEmptyAnswerFallsBackToRules() {
        assertEquals(TimelinePlanner.GENERATED_BY_RULE, plan(new FakeGemini(true, "일정을 짜드릴게요!"), stops).generatedBy());
        assertEquals(TimelinePlanner.GENERATED_BY_RULE, plan(new FakeGemini(true, null), stops).generatedBy());
    }

    @Test
    void geminiIsNotCalledWithoutApiKey() {
        FakeGemini gemini = new FakeGemini(false, "{}");

        TimelinePlanner.Result result = plan(gemini, stops);

        assertEquals(TimelinePlanner.GENERATED_BY_RULE, result.generatedBy());
        assertEquals(0, gemini.calls.get());
    }

    @Test
    void aiPlanWithLongerRouteThanRulesFallsBackToRules() {
        // 가까운 둘(35.100, 35.101)과 약 5.6km 떨어진 둘(35.150, 35.151). 도보 적정 거리 2km
        PlanningPlace a = place("A", TravelCategory.CULTURE, 35.100);
        PlanningPlace b = place("B", TravelCategory.CULTURE, 35.150);
        PlanningPlace c = place("C", TravelCategory.CULTURE, 35.101);
        PlanningPlace d = place("D", TravelCategory.CULTURE, 35.151);
        List<TimelineScheduler.Stop> scattered = List.of(
                new TimelineScheduler.Stop(a, null), new TimelineScheduler.Stop(b, null),
                new TimelineScheduler.Stop(c, null), new TimelineScheduler.Stop(d, null));
        // AI 가 하루에 A → B → C → D 로 왔다 갔다 하게 짠 경우
        FakeGemini gemini = new FakeGemini(true, """
                {"days":[{"date":"2026-10-03","visits":[
                  {"stop_id":0,"stay_minutes":60},{"stop_id":1,"stay_minutes":60},
                  {"stop_id":2,"stay_minutes":60},{"stop_id":3,"stay_minutes":60}]}]}
                """);

        TimelinePlanner.Result result = plan(gemini, scattered);

        // 규칙 기반: 첫날 A, C / 다음 날 B, D (먼 이동은 다음 날로)
        assertEquals(TimelinePlanner.GENERATED_BY_RULE, result.generatedBy());
        assertEquals(List.of("A", "C", "B", "D"), visitNames(result));
        assertEquals(DAY1.plusDays(1), result.items().get(result.items().size() - 1).date());
        assertTrue(result.items().stream()
                .filter(item -> item.type() == ItineraryItemType.MOVE)
                .noneMatch(item -> TransportRange.isLongDistance(TransportType.WALK, item.distanceMeters())));
    }
}
