package com.olawale.budgettracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.olawale.budgettracker.security.JwtAuthenticationFilter;
import com.olawale.budgettracker.security.RateLimitFilter;
import com.olawale.budgettracker.service.JwtService;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.rate-limit.auth-requests-per-minute:10}")
    private long authRequestsPerMinute;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Tomcat's internal forward to /error (404/500 rendering) re-enters the
                // chain anonymously; without this, every 404 on a protected path
                // would be reported as a 401. The original request was already authorized.
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            // Both anchor on UsernamePasswordAuthenticationFilter (a registered position).
            // Insertion order = chain order: RateLimit lands first, JwtAuth second.
            // Rate limiting runs BEFORE JWT verification so brute-forcers are throttled
            // before we ever spend CPU on signature checks or bcrypt.
            .addFilterBefore(new RateLimitFilter(authRequestsPerMinute), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
            // With httpBasic removed, Spring has no default 401 door — without this,
            // anonymous requests would get 403. A missing token is 401 + WWW-Authenticate.
            .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                response.setStatus(401);
                response.setHeader("WWW-Authenticate", "Bearer");
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"status\":401,\"message\":\"Authentication required\"}");
            }));

        return http.build();
    }
}
