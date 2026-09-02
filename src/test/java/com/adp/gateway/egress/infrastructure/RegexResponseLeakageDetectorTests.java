package com.adp.gateway.egress.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import org.junit.jupiter.api.Test;

class RegexResponseLeakageDetectorTests {

    private final RegexResponseLeakageDetector detector =
        new RegexResponseLeakageDetector(new CanonicalValueHasher());

    @Test
    void detectsSensitiveResponseWithoutPersistingEvidenceText() {
        var findings = detector.detect(payload(), Map.of("answer", "Call 010-1234-5678"));

        assertThat(findings).hasSize(1);
        assertThat(findings.getFirst().findingType()).isEqualTo("PHONE_NUMBER");
        assertThat(findings.getFirst().evidenceDigest()).hasSize(64);
        assertThat(findings.getFirst().toString()).doesNotContain("010-1234-5678");
    }

    private OutboundCandidatePayload payload() {
        return new OutboundCandidatePayload(
            "out_test",
            "dest_test",
            "v1",
            "profile_digest",
            ExecutionPackType.AI,
            "schema-v1",
            "candidate_digest",
            List.of()
        );
    }
}
