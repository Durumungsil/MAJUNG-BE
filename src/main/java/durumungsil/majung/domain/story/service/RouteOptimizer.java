package durumungsil.majung.domain.story.service;

import durumungsil.majung.domain.place.PlanningPlace;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

/**
 * 타임라인 방문 순서 최적화 (규칙 기반 배치용).
 * <ol>
 *   <li>방문일이 정해진 장소는 날짜별로 묶는다.</li>
 *   <li>방문일이 없는 장소는 이동 거리가 가장 적게 늘어나는 자리에 끼워 넣는다 (같은 날 앞뒤 장소와의 거리 기준).
 *       첫 번째 정해진 날짜보다 앞의 빈 날에도 들어갈 수 있다. 모든 장소가 방문일이 없으면 이 단계 없이 한 묶음으로 본다.</li>
 *   <li>같은 날로 묶인 장소들은 이동 거리 합이 가장 짧은 순서(열린 경로)로 다시 정렬한다:
 *       모든 출발점에서 최근접 이웃으로 경로를 만들고 2-opt 로 다듬은 뒤 가장 짧은 것을 고른다.</li>
 * </ol>
 * 날짜를 실제로 나누는 일(하루 일정 초과, 먼 이동)은 {@link TimelineScheduler} 가 한다.
 * 결과는 입력에만 의존한다 (동률이면 입력 순서가 앞선 쪽).
 */
public final class RouteOptimizer {

    /** 모든 출발점을 시도하는 최대 장소 수. 넘으면 첫 장소에서만 출발한다 */
    static final int MAX_STARTS = 40;

    private RouteOptimizer() {
    }

    public static List<TimelineScheduler.Stop> optimize(List<TimelineScheduler.Stop> stops, LocalDate startDate) {
        if (stops.stream().allMatch(stop -> stop.fixedDate() == null)) {
            return bestOpenPath(stops);
        }
        // 1) 방문일이 정해진 장소: 날짜 순 블록
        TreeMap<LocalDate, List<TimelineScheduler.Stop>> fixed = new TreeMap<>();
        List<TimelineScheduler.Stop> free = new ArrayList<>();
        for (TimelineScheduler.Stop stop : stops) {
            if (stop.fixedDate() != null) {
                fixed.computeIfAbsent(stop.fixedDate(), key -> new ArrayList<>()).add(stop);
            } else {
                free.add(stop);
            }
        }
        List<TimelineScheduler.Stop> sequence = new ArrayList<>();
        fixed.values().forEach(sequence::addAll);

        // 2) 방문일이 없는 장소: 비용이 가장 적게 늘어나는 자리에 끼워 넣기
        for (TimelineScheduler.Stop stop : free) {
            int bestPosition = sequence.size();
            double bestCost = Double.MAX_VALUE;
            double before = cost(sequence, startDate);
            // 비용이 같으면 뒤쪽(받은 순서에 가까운 자리)을 고른다
            for (int position = sequence.size(); position >= 0; position--) {
                sequence.add(position, stop);
                double added = cost(sequence, startDate) - before;
                sequence.remove(position);
                if (added < bestCost - 1e-6) {
                    bestCost = added;
                    bestPosition = position;
                }
            }
            sequence.add(bestPosition, stop);
        }

        // 3) 같은 날 블록마다 최단 순서로
        List<TimelineScheduler.Stop> result = new ArrayList<>();
        List<TimelineScheduler.Stop> block = new ArrayList<>();
        LocalDate blockDate = null;
        LocalDate[] dates = effectiveDates(sequence, startDate);
        for (int i = 0; i < sequence.size(); i++) {
            if (blockDate != null && !blockDate.equals(dates[i])) {
                result.addAll(bestOpenPath(block));
                block.clear();
            }
            blockDate = dates[i];
            block.add(sequence.get(i));
        }
        result.addAll(bestOpenPath(block));
        return result;
    }

    /** 장소 사이 직선거리(m). 좌표가 없으면 0 */
    static double meters(PlanningPlace from, PlanningPlace to) {
        if (from.latitude() == null || from.longitude() == null
                || to.latitude() == null || to.longitude() == null) {
            return 0;
        }
        return to.distanceMetersFrom(from.latitude(), from.longitude());
    }

    /** 열린 경로 길이(m) */
    static double pathLength(List<TimelineScheduler.Stop> path) {
        double total = 0;
        for (int i = 1; i < path.size(); i++) {
            total += meters(path.get(i - 1).place(), path.get(i).place());
        }
        return total;
    }

    /**
     * 각 자리의 실제 방문일 어림값: 방문일이 정해진 장소는 그 날, 아니면 바로 앞 장소의 날(맨 앞이면 여행 시작일).
     */
    private static LocalDate[] effectiveDates(List<TimelineScheduler.Stop> sequence, LocalDate startDate) {
        LocalDate[] dates = new LocalDate[sequence.size()];
        LocalDate current = startDate;
        for (int i = 0; i < sequence.size(); i++) {
            LocalDate fixedDate = sequence.get(i).fixedDate();
            if (fixedDate != null) {
                current = fixedDate;
            }
            dates[i] = current;
        }
        return dates;
    }

    /** 같은 날 안의 이동 거리 합. 날이 바뀌는 사이는 이동이 없다 */
    private static double cost(List<TimelineScheduler.Stop> sequence, LocalDate startDate) {
        LocalDate[] dates = effectiveDates(sequence, startDate);
        double total = 0;
        for (int i = 1; i < sequence.size(); i++) {
            if (dates[i].equals(dates[i - 1])) {
                total += meters(sequence.get(i - 1).place(), sequence.get(i).place());
            }
        }
        return total;
    }

    /** 이동 거리 합이 가장 짧은 열린 경로 */
    static List<TimelineScheduler.Stop> bestOpenPath(List<TimelineScheduler.Stop> stops) {
        int n = stops.size();
        if (n <= 2) {
            return new ArrayList<>(stops);
        }
        double[][] distance = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                distance[i][j] = distance[j][i] = meters(stops.get(i).place(), stops.get(j).place());
            }
        }
        int[] best = null;
        double bestLength = Double.MAX_VALUE;
        int starts = n <= MAX_STARTS ? n : 1;
        for (int start = 0; start < starts; start++) {
            int[] path = twoOpt(nearestNeighbor(start, distance), distance);
            double length = length(path, distance);
            if (length < bestLength - 1e-6) {
                bestLength = length;
                best = path;
            }
        }
        List<TimelineScheduler.Stop> ordered = new ArrayList<>(n);
        for (int index : best) {
            ordered.add(stops.get(index));
        }
        return Collections.unmodifiableList(ordered);
    }

    private static int[] nearestNeighbor(int start, double[][] distance) {
        int n = distance.length;
        int[] path = new int[n];
        boolean[] visited = new boolean[n];
        path[0] = start;
        visited[start] = true;
        for (int step = 1; step < n; step++) {
            int from = path[step - 1];
            int next = -1;
            for (int candidate = 0; candidate < n; candidate++) {
                if (!visited[candidate] && (next < 0 || distance[from][candidate] < distance[from][next] - 1e-6)) {
                    next = candidate;
                }
            }
            path[step] = next;
            visited[next] = true;
        }
        return path;
    }

    /** 열린 경로 2-opt: 구간 [i, j] 를 뒤집어 짧아지면 반영한다. 끝점도 바뀔 수 있다 */
    private static int[] twoOpt(int[] path, double[][] distance) {
        int n = path.length;
        boolean improved = true;
        while (improved) {
            improved = false;
            for (int i = 0; i < n - 1; i++) {
                for (int j = i + 1; j < n; j++) {
                    double before = (i > 0 ? distance[path[i - 1]][path[i]] : 0)
                            + (j < n - 1 ? distance[path[j]][path[j + 1]] : 0);
                    double after = (i > 0 ? distance[path[i - 1]][path[j]] : 0)
                            + (j < n - 1 ? distance[path[i]][path[j + 1]] : 0);
                    if (after < before - 1e-6) {
                        for (int left = i, right = j; left < right; left++, right--) {
                            int swap = path[left];
                            path[left] = path[right];
                            path[right] = swap;
                        }
                        improved = true;
                    }
                }
            }
        }
        return path;
    }

    private static double length(int[] path, double[][] distance) {
        double total = 0;
        for (int i = 1; i < path.length; i++) {
            total += distance[path[i - 1]][path[i]];
        }
        return total;
    }
}
