package com.adp.gateway.auth.domain;

import java.util.Collection;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public record AuthenticatedPrincipal(
    AuthPrincipal principal,
    Collection<? extends GrantedAuthority> authorities
) implements Authentication {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getDetails() {
        return AuthContext.from(principal);
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public boolean isAuthenticated() {
        return true;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) {
        if (!isAuthenticated) {
            throw new IllegalArgumentException("AuthenticatedPrincipal cannot be marked unauthenticated");
        }
    }

    @Override
    public String getName() {
        return principal.principalId();
    }
}
