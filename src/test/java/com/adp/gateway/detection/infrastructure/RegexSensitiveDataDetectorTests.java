package com.adp.gateway.detection.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.context.domain.CanonicalContextField;
import com.adp.gateway.detection.domain.SensitiveDataType;
import com.adp.gateway.retrieval.domain.DataClass;
import org.junit.jupiter.api.Test;

class RegexSensitiveDataDetectorTests {

    private final RegexSensitiveDataDetector detector = new RegexSensitiveDataDetector(new CanonicalValueHasher());

    @Test
    void detectsSensitiveTypesWithLocationAndVersion() {
        CanonicalContext context = new CanonicalContext(
            CanonicalContext.SCHEMA_VERSION,
            "ctx_test",
            "da_test",
            "customer_summary",
            "CUSTOMER_SUPPORT",
            "customer",
            "digest",
            List.of(
                new CanonicalContextField(
                    "$.records[0].customer.email",
                    "customer",
                    "email",
                    DataClass.CUSTOMER_IDENTIFIER,
                    "minji.kim@example.test",
                    "field_digest_1"
                ),
                new CanonicalContextField(
                    "$.records[1].account.account_number",
                    "account",
                    "account_number",
                    DataClass.ACCOUNT_IDENTIFIER,
                    "110-123-456789",
                    "field_digest_2"
                ),
                new CanonicalContextField(
                    "$.records[2].customer.rrn",
                    "customer",
                    "rrn",
                    DataClass.CUSTOMER_IDENTIFIER,
                    "900101-2000000",
                    "field_digest_3"
                )
            ),
            "context_digest"
        );

        var result = detector.detect(context);

        assertThat(result.detectorVersion()).isEqualTo("regex-dev-1");
        assertThat(result.findings())
            .extracting("type")
            .contains(
                SensitiveDataType.EMAIL,
                SensitiveDataType.ACCOUNT_NUMBER,
                SensitiveDataType.RESIDENT_REGISTRATION_NUMBER
            );
        assertThat(result.findings())
            .allSatisfy(finding -> {
                assertThat(finding.contextPath()).startsWith("$.records[");
                assertThat(finding.detectorVersion()).isEqualTo("regex-dev-1");
                assertThat(finding.evidenceDigest()).hasSize(64);
                assertThat(finding.endOffset()).isGreaterThan(finding.startOffset());
            });
    }

    @Test
    void treatsPhoneNumberAsPhoneWithoutAccountNumberCollision() {
        CanonicalContext context = new CanonicalContext(
            CanonicalContext.SCHEMA_VERSION,
            "ctx_test",
            "da_test",
            "customer_summary",
            "CUSTOMER_SUPPORT",
            "customer",
            "digest",
            List.of(new CanonicalContextField(
                "$.records[0].customer.phone_number",
                "customer",
                "phone_number",
                DataClass.CUSTOMER_IDENTIFIER,
                "010-1111-2222",
                "field_digest"
            )),
            "context_digest"
        );

        var result = detector.detect(context);

        assertThat(result.findings())
            .extracting("type")
            .containsExactly(SensitiveDataType.PHONE_NUMBER);
    }
}
