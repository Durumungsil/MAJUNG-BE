package durumungsil.majung.global.places;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;

@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(PlacesProperties.class)
public class PlacesConfig {
    @Bean
    public Clock appClock() {
        return Clock.systemUTC();
    }

    @Bean("placesExecutor")
    public ThreadPoolTaskExecutor placesExecutor() {
        return pool("places-", 4, 32);
    }

    @Bean("profileExecutor")
    public ThreadPoolTaskExecutor profileExecutor() {
        return pool("profile-", 2, 32);
    }

    private ThreadPoolTaskExecutor pool(String prefix, int size, int queue) {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(size);
        pool.setMaxPoolSize(size);
        pool.setQueueCapacity(queue);
        pool.setThreadNamePrefix(prefix);
        pool.setWaitForTasksToCompleteOnShutdown(true);
        pool.setAwaitTerminationSeconds(35);
        return pool;
    }
}
