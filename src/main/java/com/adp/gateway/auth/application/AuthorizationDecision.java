package com.adp.gateway.auth.application;

public record AuthorizationDecision(boolean allowed) {

    public static AuthorizationDecision allow() {
        return new AuthorizationDecision(true);
    }

    public static AuthorizationDecision deny() {
        return new AuthorizationDecision(false);
    }
}
