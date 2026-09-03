package com.adp.gateway.runtime.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class RuntimeRequestHasherTests {

    private final RuntimeRequestHasher hasher = new RuntimeRequestHasher(new ObjectMapper());

    @Test
    void hashIsStableAcrossMapAndProcessingContextOrder() {
        Map<String, Object> firstInput = new LinkedHashMap<>();
        firstInput.put("prompt", "approved");
        firstInput.put("options", Map.of("region", "KR", "retention", "NONE"));
        Map<String, Object> secondInput = new LinkedHashMap<>();
        secondInput.put("options", Map.of("retention", "NONE", "region", "KR"));
        secondInput.put("prompt", "approved");

        assertThat(hash(List.of("AI_USE", "CUSTOMER_SUPPORT"), firstInput))
            .isEqualTo(hash(List.of("CUSTOMER_SUPPORT", "AI_USE"), secondInput));
    }

    @Test
    void hashChangesWhenExecutionMeaningChanges() {
        String baseline = hash(List.of("AI_USE"), Map.of("prompt", "ticket-100"));

        assertThat(hash(List.of("AI_USE"), Map.of("prompt", "ticket-999"))).isNotEqualTo(baseline);
        assertThat(hasher.hash(
            "institution_local",
            "approval_other",
            "customer_summary",
            "CUSTOMER_SUPPORT",
            "customer:customer-100",
            "dest_internal_provider_project_provisional",
            List.of("AI_USE"),
            Map.of("prompt", "ticket-100")
        )).isNotEqualTo(baseline);
    }

    @Test
    void duplicateProcessingContextsHaveSetSemantics() {
        assertThat(hash(List.of("AI_USE", "AI_USE"), Map.of("prompt", "ticket-100")))
            .isEqualTo(hash(List.of("AI_USE"), Map.of("prompt", "ticket-100")));
    }

    private String hash(List<String> processingContexts, Map<String, Object> input) {
        return hasher.hash(
            "institution_local",
            "approval_ai_customer_support_v1",
            "customer_summary",
            "CUSTOMER_SUPPORT",
            "customer:customer-100",
            "dest_internal_provider_project_provisional",
            processingContexts,
            input
        );
    }
}
