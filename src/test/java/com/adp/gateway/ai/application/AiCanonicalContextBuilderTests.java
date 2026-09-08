package com.adp.gateway.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.context.application.ExecutionPackInputRejectedException;
import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.detection.infrastructure.RegexSensitiveDataDetector;
import com.adp.gateway.retrieval.domain.DataClass;
import org.junit.jupiter.api.Test;

class AiCanonicalContextBuilderTests {

    private final CanonicalValueHasher hasher = new CanonicalValueHasher();
    private final AiCanonicalContextBuilder builder = new AiCanonicalContextBuilder(
        hasher,
        new RegexSensitiveDataDetector(hasher)
    );

    @Test
    void addsSafePromptToTheSameCanonicalContextAsRetrievedRagFields() {
        CanonicalContext result = builder.merge(
            context(), Map.of("prompt", "Summarize the approved context"), requestScope()
        );

        assertThat(result.fields()).hasSize(1);
        assertThat(result.fields().getFirst().path()).isEqualTo("$.input.prompt");
        assertThat(result.fields().getFirst().dataClass()).isEqualTo(DataClass.BUSINESS_METADATA);
        assertThat(result.contextDigest()).isNotEqualTo(context().contextDigest());
    }

    @Test
    void marksPromptAsUnknownWhenSensitiveDataIsDetectedSoPolicyCannotAllowIt() {
        CanonicalContext result = builder.merge(context(), Map.of("prompt", "Contact 010-1234-5678"), requestScope());

        assertThat(result.fields().getFirst().dataClass()).isEqualTo(DataClass.UNKNOWN);
        assertThat(result.toString()).doesNotContain("010-1234-5678");
    }

    @Test
    void rejectsUnapprovedInputKeysWithoutEchoingValues() {
        assertThatThrownBy(() -> builder.merge(
            context(), Map.of("prompt", "safe", "rawCustomer", "secret"), requestScope()
        ))
            .isInstanceOf(ExecutionPackInputRejectedException.class)
            .hasMessageNotContaining("secret");
    }

    private CanonicalContext context() {
        return new CanonicalContext(
            CanonicalContext.SCHEMA_VERSION,
            "ctx_test",
            "access_test",
            "customer_summary",
            "CUSTOMER_SUPPORT",
            "customer",
            "subject_digest",
            List.of(),
            "retrieval_digest"
        );
    }

    private com.adp.gateway.context.application.ExecutionPackRequestScope requestScope() {
        return new com.adp.gateway.context.application.ExecutionPackRequestScope(
            "institution_test", "customer_summary", "CUSTOMER_SUPPORT", "subject_digest",
            "dest_test", java.time.OffsetDateTime.parse("2026-09-08T00:00:00Z")
        );
    }
}
