package durumungsil.majung.domain.adventure;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AdventureDtos {
    private AdventureDtos() { }

    public record CreateRequest(@JsonProperty("travel_date") LocalDate travelDate,
                                @JsonProperty("mission_count") Integer missionCount) { }
    public record StartRequest(Double latitude, Double longitude) { }
    public record CompleteRequest(Double latitude, Double longitude,
                                  @JsonProperty("completed_at") Instant completedAt) { }
    public record Created(@JsonProperty("process_id") Long processId,
                          @JsonProperty("group_id") Long groupId, AdventureStatus status,
                          @JsonProperty("mission_count") int missionCount,
                          @JsonProperty("destination_count") int destinationCount,
                          @JsonProperty("first_mission_id") Long firstMissionId,
                          @JsonProperty("created_at") Instant createdAt) { }
    public record Progress(int completed, int total) { }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MissionProgress(Long missionId, @JsonProperty("order") int order,
                                  MissionStatus status, Instant completedAt) { }
    public record ProcessView(@JsonProperty("process_id") Long processId, AdventureStatus status,
                              Progress progress, @JsonProperty("current_missionId") Long currentMissionId,
                              List<MissionProgress> missions) { }
    public record CurrentMission(@JsonProperty("mission_id") Long missionId, @JsonProperty("order") int order,
                                 MissionStatus status, String title, String objective,
                                 @JsonProperty("hint_count") int hintCount,
                                 @JsonProperty("started_at") Instant startedAt) { }
    public record Started(@JsonProperty("mission_id") Long missionId,
                          @JsonProperty("started_at") Instant startedAt) { }
    public record Hint(@JsonProperty("mission_id") Long missionId, double latitude, double longitude,
                       @JsonProperty("view_seconds") int viewSeconds,
                       @JsonProperty("hint_count") int hintCount) { }
    public record Completed(@JsonProperty("mission_id") Long missionId, MissionStatus status,
                            @JsonProperty("next_missionId") Long nextMissionId,
                            @JsonProperty("completed_at") Instant completedAt) { }
}
