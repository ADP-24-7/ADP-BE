package com.adp.gateway.auth.application;

import java.util.Optional;

import com.adp.gateway.auth.domain.AuthPrincipal;

public interface AuthPrincipalLookup {

    Optional<AuthPrincipal> findByApiKeyHash(String apiKeyHash);
}
