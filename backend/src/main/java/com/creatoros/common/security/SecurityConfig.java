package com.creatoros.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security config — skeleton only.
 *
 * For now it:
 *   - Disables CSRF (REST API, stateless)
 *   - Sets session management to STATELESS
 *   - Opens /api/v1/health and /api/v1/auth/** publicly
 *   - Locks everything else behind authentication
 *
 * JWT filter will be wired in during Week 2 (auth step).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // ── Public endpoints ───────────────────────────────────────────────────
    private static final String[] PUBLIC_PATHS = {
            "/api/v1/health",
            "/api/v1/auth/register",
            "/api/v1/auth/login"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — REST APIs use tokens, not cookies
            .csrf(AbstractHttpConfigurer::disable)

            // Stateless session — JWT handles state
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(PUBLIC_PATHS).permitAll()
                    .anyRequest().authenticated()
            );

        // JWT filter will be added here in Week 2:
        // http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ── Password encoder ───────────────────────────────────────────────────
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
