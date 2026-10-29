package durumungsil.majung.domain.preference.profile;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class ProfileWorker {
    private final ProfileTransactions transactions;
    private final ProfileGenerator generator;
    @Async("profileExecutor")
    public void generate(ProfileJob job) {
        transactions.claim(job).ifPresent(work->{
            try { transactions.finish(work,generator.generate(work.rules(),work.rawAllergies(),work.region())); }
            catch(RuntimeException error) { transactions.fail(work); }
        });
    }
}
