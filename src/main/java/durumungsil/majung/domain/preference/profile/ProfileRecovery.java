package durumungsil.majung.domain.preference.profile;

import lombok.RequiredArgsConstructor;

import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.*;

@Component
@RequiredArgsConstructor
public class ProfileRecovery {
    private final PreferenceProfileRepository repository;
    private final ProfileWorker worker;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${preference.profile.recovery-interval-ms:60000}")
    public void retryStale() {
        for (Long id :
                repository.findStale(
                        clock.instant().minus(Duration.ofMinutes(5)), PageRequest.of(0, 20))) {
            repository
                    .findById(id)
                    .ifPresent(
                            profile -> {
                                try {
                                    worker.generate(new ProfileJob(id, profile.getVersion()));
                                } catch (TaskRejectedException busy) {
                                    /* 다음 스케줄에서 다시 시도한다. */
                                }
                            });
        }
    }
}
