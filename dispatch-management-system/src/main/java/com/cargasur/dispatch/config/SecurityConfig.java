package com.cargasur.dispatch.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {

    private static final String OPERATOR = "OPERATOR";
    private static final String VIEWER = "VIEWER";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, ex) -> writeProblem(
                response, HttpServletResponse.SC_UNAUTHORIZED, "No autenticado",
                "Credenciales ausentes o inválidas", true);
        AccessDeniedHandler forbidden = (request, response, ex) -> writeProblem(
                response, HttpServletResponse.SC_FORBIDDEN, "Acceso denegado",
                "El usuario autenticado no tiene permiso para esta operación", false);

        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/**").hasAnyRole(OPERATOR, VIEWER)
                        .anyRequest().hasRole(OPERATOR))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .httpBasic(basic -> basic.authenticationEntryPoint(unauthorized))
                .build();
    }

    @Bean
    UserDetailsService userDetailsService(
            @Value("${app.auth.username}") String operatorUsername,
            @Value("${app.auth.password}") String operatorPassword,
            @Value("${app.auth.viewer-username}") String viewerUsername,
            @Value("${app.auth.viewer-password}") String viewerPassword,
            PasswordEncoder passwordEncoder
    ) {
        var operator = User.withUsername(operatorUsername)
                .password(passwordEncoder.encode(operatorPassword))
                .roles(OPERATOR)
                .build();
        var viewer = User.withUsername(viewerUsername)
                .password(passwordEncoder.encode(viewerPassword))
                .roles(VIEWER)
                .build();
        return new InMemoryUserDetailsManager(operator, viewer);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private static void writeProblem(HttpServletResponse response, int status, String title, String detail,
                                     boolean challenge) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (challenge) {
            response.setHeader("WWW-Authenticate", "Basic realm=\"CargaSur\"");
        }
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title
                + "\",\"status\":" + status + ",\"detail\":\"" + detail + "\"}");
    }
}
