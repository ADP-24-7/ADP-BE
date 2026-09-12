package com.adp.gateway.evidence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class RegulatoryRefreshServiceTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void manualAndScheduledCallsPreserveNoAutomaticActivationBoundary() throws Exception {
        JsonNode report = mapper.readTree("""
            {"automatic_activation":false,"results":[{"status":"UNCHANGED"}]}
            """);
        RegulatoryRefreshService service = new RegulatoryRefreshService(sourceIds -> report);

        assertThat(service.refresh(List.of("AI-1"))).isSameAs(report);
        assertThat(service.refresh(null)).isSameAs(report);
    }

    @Test
    void rejectsAnyAttemptToAutomaticallyActivatePolicy() throws Exception {
        JsonNode report = mapper.readTree("""
            {"automatic_activation":true,"results":[{"status":"CHANGED"}]}
            """);
        RegulatoryRefreshService service = new RegulatoryRefreshService(sourceIds -> report);

        assertThatThrownBy(() -> service.refresh(List.of()))
            .isInstanceOf(ReferenceEvidenceException.class)
            .hasMessage("REGULATORY_AUTOMATIC_ACTIVATION_FORBIDDEN");
    }
}
