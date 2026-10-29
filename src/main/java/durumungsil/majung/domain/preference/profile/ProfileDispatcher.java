package durumungsil.majung.domain.preference.profile;

import lombok.RequiredArgsConstructor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ProfileDispatcher {
    private final ProfileWorker worker;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void committed(ProfileJob job) {
        try {
            worker.generate(job);
        } catch (TaskRejectedException busy) {
            // 설문 저장은 이미 완료됐다. PENDING을 유지하면 복구 스케줄러가 재실행한다.
        }
    }
}
