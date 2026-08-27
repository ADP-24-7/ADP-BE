package com.adp.gateway.context.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.adp.gateway.dataaccess.application.SubjectRefHasher;
import com.adp.gateway.retrieval.domain.DataClass;
import com.adp.gateway.retrieval.domain.RetrievalDatasetScope;
import com.adp.gateway.retrieval.domain.RetrievalField;
import com.adp.gateway.retrieval.domain.RetrievalRecord;
import com.adp.gateway.retrieval.domain.RetrievalResult;
import org.junit.jupiter.api.Test;

class CanonicalContextBuilderTests {

    private final CanonicalContextBuilder builder = new CanonicalContextBuilder(
        new CanonicalValueHasher(),
        new SubjectRefHasher()
    );

    @Test
    void buildsCanonicalContextFromRetrievalResult() {
        RetrievalResult result = retrievalResult(
            List.of(
                new RetrievalField("customer", "customer_id", DataClass.CUSTOMER_IDENTIFIER),
                new RetrievalField("customer", "segment", DataClass.BUSINESS_METADATA)
            ),
            List.of(new RetrievalRecord("customer", Map.of(
                "customer_id", "customer-100",
                "segment", "preferred",
                "unselected_raw_field", "must-drop"
            )))
        );

        var context = builder.build(result);

        assertThat(context.contextId()).startsWith("ctx_");
        assertThat(context.dataAccessId()).isEqualTo("da_test");
        assertThat(context.workloadId()).isEqualTo("customer_summary");
        assertThat(context.subjectRefDigest()).hasSize(64);
        assertThat(context.contextDigest()).hasSize(64);
        assertThat(context.fields()).hasSize(2);
        assertThat(context.fields())
            .extracting("fieldName")
            .containsExactly("customer_id", "segment");
        assertThat(context.fields())
            .extracting("fieldName")
            .doesNotContain("unselected_raw_field");
    }

    @Test
    void keepsUnknownDataClassAsDecisionInputMetadata() {
        RetrievalResult result = retrievalResult(
            List.of(new RetrievalField("customer", "risk_note", DataClass.UNKNOWN)),
            List.of(new RetrievalRecord("customer", Map.of("risk_note", "manual review required")))
        );

        var context = builder.build(result);

        assertThat(context.fields()).hasSize(1);
        assertThat(context.fields().getFirst().dataClass()).isEqualTo(DataClass.UNKNOWN);
        assertThat(context.fields().getFirst().hasUnknownDataClass()).isTrue();
    }

    private RetrievalResult retrievalResult(
        List<RetrievalField> fields,
        List<RetrievalRecord> records
    ) {
        return new RetrievalResult(
            "da_test",
            "customer_summary",
            "CUSTOMER_SUPPORT",
            "customer",
            "customer-100",
            "profile_customer_summary_support",
            records.size(),
            List.of(new RetrievalDatasetScope("customer", 1, null)),
            fields,
            records
        );
    }
}
