package com.aidocker.agent.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationSuccessHandler authenticationSuccessHandler
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/health", "/actuator/**", "/api/auth/me").permitAll()
                        .requestMatchers("/api/conversations/**").authenticated()
                        .requestMatchers("/api/repositories/**").authenticated()
                        .requestMatchers("/api/analysis/**").authenticated()
                        .requestMatchers("/api/debug/**").authenticated()
                        .requestMatchers("/api/deployment-permissions/**").authenticated()
                        .anyRequest().permitAll()
                )
                .oauth2Login(oauth2 -> oauth2.successHandler(authenticationSuccessHandler))
                .logout(logout -> logout.logoutSuccessUrl("/api/auth/me"))
                .build();
    }

    @Bean
    AuthenticationSuccessHandler authenticationSuccessHandler(
            @Value("${app.frontend-base-url}") String frontendBaseUrl
    ) {
        return (request, response, authentication) -> response.sendRedirect(frontendBaseUrl);
    }
}
