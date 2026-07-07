package com.hic.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
             .authorizeHttpRequests(auth -> auth
                 .requestMatchers("/api/auth/login").permitAll()
                 .requestMatchers("/api/auth/signup").permitAll()
                 .requestMatchers("/api/auth/refresh").permitAll()
                 .requestMatchers("/api/auth/verify").permitAll()
                 .requestMatchers("/api/health").permitAll()
                 .requestMatchers("/api/isapi/access-control/user-info/record").permitAll()
                 .requestMatchers("/ISAPI/AccessControl/UserInfo/Record").permitAll()
                 .requestMatchers("/actuator/health").permitAll()
                 .anyRequest().authenticated()
             )
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
