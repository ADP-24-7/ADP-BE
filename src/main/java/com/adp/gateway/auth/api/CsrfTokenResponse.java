package com.adp.gateway.auth.api;

public record CsrfTokenResponse(String headerName, String token) {
}
