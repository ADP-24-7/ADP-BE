package com.adp.gateway.auth.infrastructure;

import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;

import com.adp.gateway.auth.application.ApiKeyHasher;
import com.adp.gateway.auth.application.AuthPrincipalLookup;
import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthenticatedPrincipal;
import com.adp.gateway.auth.domain.PrincipalType;
import com.adp.gateway.common.error.ErrorResponse;
import com.adp.gateway.common.error.ReasonCode;
import com.adp.gateway.common.trace.TraceHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new ChangeSessionIdAuthenticationStrategy();
    }

    @Bean
    @Order(1)
    SecurityFilterChain serviceSecurityFilterChain(
        HttpSecurity http,
        ApiKeyHasher apiKeyHasher,
        AuthPrincipalLookup authPrincipalLookup,
        ObjectMapper objectMapper,
        Clock clock,
        @Value("${adp.observability.prometheus-public:false}") boolean prometheusPublic
    ) throws Exception {
        ApiKeyAuthenticationFilter apiKeyAuthenticationFilter =
            new ApiKeyAuthenticationFilter(apiKeyHasher, authPrincipalLookup, objectMapper, clock);

        http
            .securityMatcher("/v1/**", "/api/runtime/**", "/api/internal/**", "/actuator/**")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/actuator/health/**", "/actuator/info", "/api/internal/info").permitAll();
                if (prometheusPublic) {
                    auth.requestMatchers("/actuator/prometheus").permitAll();
                } else {
                    auth.requestMatchers("/actuator/prometheus")
                        .access((authentication, context) -> {
                            var resolved = authentication.get();
                            boolean granted = resolved instanceof AuthenticatedPrincipal authenticated
                                && authenticated.principal().principalType() == PrincipalType.SERVICE
                                && authenticated.principal().hasRole(AdpRole.METRICS_SCRAPER);
                            return new AuthorizationDecision(granted);
                        });
                }
                auth.requestMatchers("/api/internal/auth/**").authenticated()
                    .requestMatchers("/api/runtime/**", "/v1/runtime/**").hasRole("RUNTIME_EXECUTOR")
                    .anyRequest().denyAll();
            })
            .exceptionHandling(exception -> commonExceptionHandling(exception, objectMapper, clock));

        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain adminSecurityFilterChain(
        HttpSecurity http,
        ObjectMapper objectMapper,
        Clock clock,
        SecurityContextRepository securityContextRepository,
        @Value("${adp.local-user-auth.enabled:false}") boolean localUserAuthEnabled
    ) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Lax"));

        http
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .securityContext(context -> context
                .securityContextRepository(securityContextRepository)
                .requireExplicitSave(true))
            .csrf(csrf -> {
                csrf.csrfTokenRepository(csrfRepository);
                if (localUserAuthEnabled) {
                    // The local harness supplies credentials explicitly on each request and never uses a browser session.
                    csrf.ignoringRequestMatchers(request ->
                        hasText(request.getHeader(UserHeaderAuthenticationFilter.USER_ID_HEADER))
                            && hasText(request.getHeader(UserHeaderAuthenticationFilter.USER_ROLES_HEADER))
                    );
                }
            })
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/docs", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers("/api/auth/me", "/api/auth/logout").authenticated()
                .requestMatchers("/api/admin/ai/evaluation-runs/*/bundle").hasRole("PRIVILEGED_OPERATOR")
                .requestMatchers("/api/admin/ai/evaluation-runs/*/readiness").hasRole("PRIVILEGED_OPERATOR")
                .requestMatchers("/api/admin/ai/evaluation-runs/*/contract", "/api/admin/ai/evaluation-runs/*/contract/freeze")
                    .hasRole("PRIVILEGED_OPERATOR")
                .requestMatchers("/api/admin/audit/executions/*/evidence").hasRole("PRIVILEGED_OPERATOR")
                .requestMatchers(HttpMethod.GET, "/api/admin/audit/executions/**")
                    .hasAnyRole("OPERATOR", "PRIVILEGED_OPERATOR", "AUDITOR")
                .requestMatchers(HttpMethod.POST, "/api/v1/audit-exports/*/approval").hasRole("PRIVILEGED_OPERATOR")
                .requestMatchers("/api/v1/audit-exports/**").hasAnyRole("PRIVILEGED_OPERATOR", "AUDITOR")
                .requestMatchers(HttpMethod.GET, "/api/admin/recovery/incidents/**")
                    .hasAnyRole("OPERATOR", "PRIVILEGED_OPERATOR", "AUDITOR")
                .requestMatchers(HttpMethod.POST, "/api/admin/recovery/incidents/**").hasRole("PRIVILEGED_OPERATOR")
                .requestMatchers(HttpMethod.GET, "/api/admin/operations/**")
                    .hasAnyRole("OPERATOR", "PRIVILEGED_OPERATOR", "AUDITOR")
                .requestMatchers(HttpMethod.GET, "/api/admin/review-queue/**")
                    .hasAnyRole("OPERATOR", "PRIVILEGED_OPERATOR", "AUDITOR")
                .requestMatchers(HttpMethod.GET, "/api/admin/security-findings/**")
                    .hasAnyRole("OPERATOR", "PRIVILEGED_OPERATOR", "AUDITOR")
                .requestMatchers(HttpMethod.GET, "/api/admin/identities/**")
                    .hasAnyRole("OPERATOR", "PRIVILEGED_OPERATOR", "AUDITOR")
                .requestMatchers(HttpMethod.POST, "/api/admin/reference-evidence/bundles")
                    .hasRole("PRIVILEGED_OPERATOR")
                .requestMatchers(HttpMethod.GET, "/api/admin/reference-evidence/**")
                    .hasAnyRole("OPERATOR", "PRIVILEGED_OPERATOR", "AUDITOR")
                .requestMatchers("/api/admin/policy-lifecycle/**")
                    .hasAnyRole("OPERATOR", "PRIVILEGED_OPERATOR", "AUDITOR")
                .requestMatchers(HttpMethod.POST, "/api/admin/digital-assets/artifacts/ingestions").hasRole("OPERATOR")
                .requestMatchers(HttpMethod.POST, "/api/admin/digital-assets/artifacts/*/versions/*/activate")
                    .hasRole("PRIVILEGED_OPERATOR")
                .requestMatchers(HttpMethod.GET, "/api/admin/digital-assets/artifacts/**")
                    .hasAnyRole("OPERATOR", "PRIVILEGED_OPERATOR", "AUDITOR")
                .requestMatchers("/api/admin/**").hasRole("OPERATOR")
                .requestMatchers("/api/privileged/**").hasRole("PRIVILEGED_OPERATOR")
                .anyRequest().denyAll())
            .exceptionHandling(exception -> commonExceptionHandling(exception, objectMapper, clock))
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                .logoutSuccessHandler((request, response, authentication) ->
                    response.setStatus(HttpStatus.NO_CONTENT.value())));

        if (localUserAuthEnabled) {
            http.addFilterBefore(new UserHeaderAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }

    private void commonExceptionHandling(
        org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer<HttpSecurity> exception,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        exception
            .authenticationEntryPoint((request, response, authException) ->
                writeError(response, objectMapper, clock, HttpStatus.UNAUTHORIZED, request))
            .accessDeniedHandler((request, response, accessDeniedException) ->
                writeError(response, objectMapper, clock, HttpStatus.FORBIDDEN, request));
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
