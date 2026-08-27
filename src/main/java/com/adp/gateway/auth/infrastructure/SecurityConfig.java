package com.adp.gateway.auth.infrastructure;

import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;

import com.adp.gateway.auth.application.ApiKeyHasher;
import com.adp.gateway.auth.application.AuthPrincipalLookup;
import com.adp.gateway.common.error.ErrorResponse;
import com.adp.gateway.common.error.ReasonCode;
import com.adp.gateway.common.trace.TraceHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        ApiKeyHasher apiKeyHasher,
        AuthPrincipalLookup authPrincipalLookup,
        ObjectMapper objectMapper,
        Clock clock
    ) throws Exception {
        ApiKeyAuthenticationFilter apiKeyAuthenticationFilter =
            new ApiKeyAuthenticationFilter(apiKeyHasher, authPrincipalLookup, objectMapper, clock);
        UserHeaderAuthenticationFilter userHeaderAuthenticationFilter = new UserHeaderAuthenticationFilter();

        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(userHeaderAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/api/internal/info").permitAll()
                .requestMatchers("/api/runtime/**").hasRole("RUNTIME_EXECUTOR")
                .requestMatchers("/api/admin/**").hasRole("OPERATOR")
                .anyRequest().authenticated()
            )
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) ->
                    writeError(response, objectMapper, clock, HttpStatus.UNAUTHORIZED, request))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    writeError(response, objectMapper, clock, HttpStatus.FORBIDDEN, request))
            );

        return http.build();
    }

    private void writeError(
        jakarta.servlet.http.HttpServletResponse response,
        ObjectMapper objectMapper,
        Clock clock,
        HttpStatus status,
        HttpServletRequest request
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");

        ReasonCode reasonCode = status == HttpStatus.UNAUTHORIZED
            ? ReasonCode.AUTHENTICATION_FAILED
            : ReasonCode.AUTHORIZATION_DENIED;

        ErrorResponse errorResponse = new ErrorResponse(
            reasonCode.name(),
            status == HttpStatus.UNAUTHORIZED ? "Authentication required" : "Authorization denied",
            attribute(request, TraceHeaders.REQUEST_ID_ATTRIBUTE),
            attribute(request, TraceHeaders.TRACE_ID_ATTRIBUTE),
            OffsetDateTime.now(clock)
        );

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }

    private String attribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value == null ? null : value.toString();
    }
}
