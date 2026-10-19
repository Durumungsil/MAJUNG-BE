package durumungsil.majung.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class AdventureSecurityConfig {
    /** 사용자 인증은 별도 작업에서 연결한다. 현재 공개 범위는 모험 API 여섯 경로로 한정한다. */
    @Bean
    @Order(1)
    SecurityFilterChain adventureSecurity(HttpSecurity http) throws Exception {
        return http.securityMatcher(
                        "/api/v1/groups/*/adventure/process", "/api/v1/groups/*/adventure/process/*",
                        "/api/v1/groups/*/missions/current", "/api/v1/groups/*/missions/*/start",
                        "/api/v1/groups/*/missions/*/hint", "/api/v1/groups/*/missions/*/complete")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain remainingSecurity(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(requests -> requests.anyRequest().denyAll()).build();
    }
}
