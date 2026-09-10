package com.adp.gateway.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.PrincipalType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserSessionAuthenticationService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final String DUMMY_PASSWORD_HASH =
        "$2y$12$Wb/A5aJaOuSTmajJyyob8OGCdeNdEk0awNHdw8fWEdZD588/ZXH1S";

    private final JdbcClient jdbcClient;
    private final AuthPrincipalLookup principalLookup;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserSessionAuthenticationService(
        JdbcClient jdbcClient,
        AuthPrincipalLookup principalLookup,
        PasswordEncoder passwordEncoder,
        Clock clock
    ) {
        this.jdbcClient = jdbcClient;
        this.principalLookup = principalLookup;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public AuthPrincipal authenticate(String principalId, String password) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Credential credential = jdbcClient.sql("""
                select c.principal_id, c.password_hash, c.failed_attempts, c.locked_until, p.enabled
                from auth_user_credential c
                join auth_principal p on p.principal_id = c.principal_id
                where c.principal_id = :principalId
                  and p.principal_type = 'USER'
                """)
            .param("principalId", principalId)
            .query((rs, rowNum) -> new Credential(
                rs.getString("principal_id"),
                rs.getString("password_hash"),
                rs.getInt("failed_attempts"),
                rs.getObject("locked_until", OffsetDateTime.class),
                rs.getBoolean("enabled")
            ))
            .optional()
            .orElse(null);

        if (credential == null) {
            passwordEncoder.matches(password, DUMMY_PASSWORD_HASH);
            throw invalidCredentials();
        }
        if (!credential.enabled() || isLocked(credential, now)) {
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(password, credential.passwordHash())) {
            recordFailure(credential, now);
            throw invalidCredentials();
        }

        jdbcClient.sql("""
                update auth_user_credential
                set failed_attempts = 0, locked_until = null, updated_at = :now
                where principal_id = :principalId
                """)
            .param("now", now)
            .param("principalId", credential.principalId())
            .update();

        AuthPrincipal principal = principalLookup.findByPrincipalId(credential.principalId())
            .filter(candidate -> candidate.principalType() == PrincipalType.USER)
            .orElseThrow(this::invalidCredentials);
        return principal;
    }

    private boolean isLocked(Credential credential, OffsetDateTime now) {
        return credential.lockedUntil() != null && credential.lockedUntil().isAfter(now);
    }

    private void recordFailure(Credential credential, OffsetDateTime now) {
        jdbcClient.sql("""
                update auth_user_credential
                set failed_attempts = failed_attempts + 1,
                    locked_until = case
                        when failed_attempts + 1 >= :maxFailedAttempts then :lockedUntil
                        else locked_until
                    end,
                    updated_at = :now
                where principal_id = :principalId
                """)
            .param("maxFailedAttempts", MAX_FAILED_ATTEMPTS)
            .param("lockedUntil", now.plus(LOCK_DURATION))
            .param("now", now)
            .param("principalId", credential.principalId())
            .update();
    }

    private BadCredentialsException invalidCredentials() {
        return new BadCredentialsException("Invalid user credentials");
    }

    private record Credential(
        String principalId,
        String passwordHash,
        int failedAttempts,
        OffsetDateTime lockedUntil,
        boolean enabled
    ) {
    }
}
