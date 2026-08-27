package com.adp.gateway.auth.domain;

public record SubjectRef(String subjectType, String subjectId) {

    private static final String DEFAULT_SUBJECT_TYPE = "customer";

    public static SubjectRef from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();
        int separator = normalized.indexOf(':');
        if (separator > 0 && separator < normalized.length() - 1) {
            return new SubjectRef(
                normalized.substring(0, separator).trim(),
                normalized.substring(separator + 1).trim()
            );
        }

        return new SubjectRef(DEFAULT_SUBJECT_TYPE, normalized);
    }
}
