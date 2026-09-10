package com.adp.gateway.operations.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.operations.application.ReviewQueueReadPort;
import com.adp.gateway.runtime.application.RuntimeExecutionNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "adp.local-fixtures.enabled=true",
    "adp.mock-runtime.enabled=true",
    "adp.local-user-auth.enabled=true"
})
@AutoConfigureMockMvc
class ReviewQueueControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReviewQueueReadPort readPort;

    @Test
    void operatorFindsReviewRequiredExecutionWithoutKnowingItsId() throws Exception {
        String executionId = createReviewRequiredExecution();

        mockMvc.perform(get("/api/admin/review-queue")
                .header("X-ADP-User-Id", "operator-local")
                .header("X-ADP-User-Roles", "OPERATOR")
                .param("executionPack", "AI")
                .param("workloadId", "customer_summary")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].executionId").value(executionId))
            .andExpect(jsonPath("$.items[0].executionPack").value("AI"))
            .andExpect(jsonPath("$.items[0].runtimeStatus").value("REVIEW_REQUIRED"))
            .andExpect(jsonPath("$.items[0].reviewSource").value("POLICY"))
            .andExpect(jsonPath("$.items[0].nextAction").value("INSPECT_TRACE"))
            .andExpect(jsonPath("$.totalElements").isNumber());

        mockMvc.perform(get("/api/admin/review-queue/{executionId}", executionId)
                .header("X-ADP-User-Id", "auditor-local")
                .header("X-ADP-User-Roles", "AUDITOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.executionId").value(executionId))
            .andExpect(jsonPath("$.executionPack").value("AI"))
            .andExpect(jsonPath("$.tracePath").value("/v1/runtime/executions/" + executionId + "/trace"))
            .andExpect(jsonPath("$.evidencePath")
                .value("/api/admin/audit/executions/" + executionId + "/evidence"));
    }

    @Test
    void readPortAlwaysAppliesInstitutionAndWorkloadScope() throws Exception {
        String executionId = createReviewRequiredExecution();

        var deniedWorkload = readPort.search(
            "institution_local", Set.of("fraud_detection"), ExecutionPackType.AI, null, 0, 10
        );
        var deniedInstitution = readPort.search(
            "institution_other", Set.of("*"), ExecutionPackType.AI, null, 0, 10
        );
        assertThat(deniedWorkload.items()).noneMatch(item -> item.executionId().equals(executionId));
        assertThat(deniedInstitution.items()).noneMatch(item -> item.executionId().equals(executionId));
        assertThatThrownBy(() -> readPort.load(
            executionId, "institution_local", Set.of("fraud_detection")
        )).isInstanceOf(RuntimeExecutionNotFoundException.class);
    }

    @Test
    void nonOperationsRoleCannotReadReviewQueue() throws Exception {
        mockMvc.perform(get("/api/admin/review-queue")
                .header("X-ADP-User-Id", "developer-local")
                .header("X-ADP-User-Roles", "DEVELOPER"))
            .andExpect(status().isForbidden());
    }

    private String createReviewRequiredExecution() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String response = mockMvc.perform(post("/v1/runtime/executions")
                .header("X-Request-Id", "req_review_queue_" + suffix)
                .header("X-Trace-Id", "trace_review_queue_" + suffix)
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "institutionId":"institution_local",
                      "approvalReference":"approval_ai_customer_support_v1",
                      "workloadId":"customer_summary",
                      "purposeCode":"CUSTOMER_SUPPORT",
                      "subjectScope":"customer:customer-100",
                      "destinationProfileId":"dest_internal_provider_project_provisional",
                      "idempotencyKey":"idem_review_queue_%s",
                      "processingContexts":["AI_USE"],
                      "input":{"prompt":"Call 010-1234-5678"}
                    }
                    """.formatted(suffix)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REVIEW_REQUIRED"))
            .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("010-1234-5678");
        return response.replaceAll(".*\\\"executionId\\\":\\\"([^\\\"]+)\\\".*", "$1");
    }
}
