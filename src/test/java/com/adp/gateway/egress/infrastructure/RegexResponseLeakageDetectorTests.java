package com.adp.gateway.egress.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.egress.domain.FieldObligation;
import com.adp.gateway.egress.domain.FieldTreatment;
import com.adp.gateway.egress.domain.OutboundCandidateField;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.retrieval.domain.DataClass;
import com.adp.gateway.transform.domain.TransformStrategy;
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

    @Test
    void detectsSecretAndCredentialFamiliesInProviderResponse() {
        var findings = detector.detect(payload(), """
            access=AKIAABCDEFGHIJKLMNOP
            refresh_token=refresh-token-value-1234
            client_secret=client-secret-value
            -----BEGIN PRIVATE KEY-----
            seed phrase=alpha bravo charlie delta echo foxtrot golf hotel india juliet kilo lima
            """);

        assertThat(findings).extracting("findingType")
            .contains("ACCESS_TOKEN", "REFRESH_TOKEN", "CREDENTIAL", "PRIVATE_KEY", "SEED");
        assertThat(findings).allSatisfy(finding -> assertThat(finding.evidenceDigest()).hasSize(64));
    }

    @Test
    void classifiesRawReflectionBySourceDataClassAndTransformWithoutExposingFieldPath() {
        var payload = new OutboundCandidatePayload(
            "out_test", "dest_test", "v1", "profile_digest", ExecutionPackType.AI,
            "schema-v1", "candidate_digest", List.of(new OutboundCandidateField(
                "$.transactions[0].transactionId",
                DataClass.TRANSACTION_IDENTIFIER,
                TransformStrategy.HMAC_PSEUDO,
                FieldObligation.PSEUDONYMIZABLE,
                FieldTreatment.TRANSFORMED,
                "value-digest",
                List.of(),
                "hmac-transaction-value"
            ))
        );

        var findings = detector.detect(payload, Map.of("answer", "Seen hmac-transaction-value"));

        assertThat(findings).hasSize(1);
        var finding = findings.getFirst();
        assertThat(finding.findingType()).isEqualTo("RAW_VALUE_REFLECTION");
        assertThat(finding.sourceDataClass()).isEqualTo("TRANSACTION_IDENTIFIER");
        assertThat(finding.transformStrategy()).isEqualTo("HMAC_PSEUDO");
        assertThat(finding.fieldTreatment()).isEqualTo("TRANSFORMED");
        assertThat(finding.outboundFieldPathDigest()).hasSize(64);
        assertThat(finding.toString()).doesNotContain("$.transactions[0].transactionId");
        assertThat(finding.toString()).doesNotContain("hmac-transaction-value");
    }

    @Test
    void countsEachReflectedOutboundFieldOccurrenceWhenValuesAreEqual() {
        var fields = List.of(
            reflectedField("$.transactions[0].transactionId"),
            reflectedField("$.transactions[1].transactionId")
        );
        var payload = new OutboundCandidatePayload(
            "out_test", "dest_test", "v1", "profile_digest", ExecutionPackType.AI,
            "schema-v1", "candidate_digest", fields
        );

        var findings = detector.detect(payload, Map.of("answer", "Seen hmac-transaction-value"));

        assertThat(findings).hasSize(2);
        assertThat(findings).allSatisfy(finding -> {
            assertThat(finding.findingType()).isEqualTo("RAW_VALUE_REFLECTION");
            assertThat(finding.sourceDataClass()).isEqualTo("TRANSACTION_IDENTIFIER");
            assertThat(finding.transformStrategy()).isEqualTo("HMAC_PSEUDO");
            assertThat(finding.fieldTreatment()).isEqualTo("TRANSFORMED");
        });
        assertThat(findings).extracting("outboundFieldPathDigest").doesNotHaveDuplicates();
    }

    private OutboundCandidateField reflectedField(String path) {
        return new OutboundCandidateField(
            path,
            DataClass.TRANSACTION_IDENTIFIER,
            TransformStrategy.HMAC_PSEUDO,
            FieldObligation.PSEUDONYMIZABLE,
            FieldTreatment.TRANSFORMED,
            "value-digest",
            List.of(),
            "hmac-transaction-value"
        );
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
