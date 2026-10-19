package durumungsil.majung.global.config;

import durumungsil.majung.global.security.SecurityErrorHandler;
import durumungsil.majung.global.security.google.GoogleProperties;
import durumungsil.majung.global.security.jwt.JwtProperties;
import durumungsil.majung.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import jakarta.servlet.DispatcherType;
import durumungsil.majung.global.security.AuthUser;
import durumungsil.majung.repository.GroupMemberRepository;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * JWT 기반 stateless 인증.
 * Authorization: Bearer {accessToken} 헤더를 검증하고, 로그인·재발급 API 외에는 모두 인증이 필요하다.
 */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, GoogleProperties.class})
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_POST_URLS = {
            "/api/v1/auth/google",
            "/api/v1/auth/refresh",
    };

    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityErrorHandler securityErrorHandler;
    private final GroupMemberRepository groupMembers;

    /** 프론트 주소 (브라우저에서 다른 포트/도메인으로 API 를 부르려면 CORS 허용 필요) */
    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String[] allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_URLS).permitAll()
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/v1/groups/{groupId}/adventure/**", "/api/v1/groups/{groupId}/missions/**")
                        .access((authentication, context) -> {
                            Object principal = authentication.get().getPrincipal();
                            if (!(principal instanceof AuthUser user)) return new AuthorizationDecision(false);
                            try {
                                Long groupId = Long.valueOf(context.getVariables().get("groupId"));
                                return new AuthorizationDecision(groupMembers.existsByGroupIdAndUserId(groupId, user.id()));
                            } catch (NumberFormatException exception) {
                                return new AuthorizationDecision(false);
                            }
                        })
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtTokenProvider.getDecoder())
                                .jwtAuthenticationConverter(jwtTokenProvider::toAuthentication))
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
