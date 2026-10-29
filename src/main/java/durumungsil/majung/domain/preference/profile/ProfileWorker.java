package durumungsil.majung.domain.preference.profile;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Component @RequiredArgsConstructor
public class ProfileWorker {
    private final ProfileTransactions transactions;
    private final ProfileGenerator generator;
    @Async("profileExecutor")
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void generate(ProfileJob job) {
        transactions.claim(job).ifPresent(work->{
            try { transactions.finish(work,generator.generate(work.rules(),work.rawAllergies(),work.region())); }
            catch(RuntimeException error) { transactions.fail(work); }
        });
    }
}
