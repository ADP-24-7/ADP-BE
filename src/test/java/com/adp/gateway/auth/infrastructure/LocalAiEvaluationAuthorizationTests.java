package com.adp.gateway.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.adp.gateway.auth.domain.RuntimeAction;
import com.adp.gateway.auth.domain.SubjectRef;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "adp.local-fixtures.enabled=true")
class LocalAiEvaluationAuthorizationTests {

    @Autowired
    private JdbcSubjectAuthorizationAdapter grants;

    @Test
    void grantsOnlyTheExactDaProvenanceEvaluationScope() {
        assertThat(canAccess("customer_summary", "CUSTOMER_SUPPORT", "da-customer-10832")).isTrue();
        assertThat(canAccess("customer_summary", "CUSTOMER_SUPPORT", "da-customer-10833")).isFalse();
        assertThat(canAccess("customer_summary", "INTERNAL_ANALYTICS", "da-customer-10832")).isFalse();
        assertThat(canAccess("customer_profile", "CUSTOMER_SUPPORT", "da-customer-10832")).isFalse();
    }

    private boolean canAccess(String workload, String purpose, String subjectId) {
        return grants.canAccess(
            "svc_local_runtime",
            workload,
            RuntimeAction.RUNTIME_EXECUTE,
            purpose,
            new SubjectRef("customer", subjectId)
        );
    }
}
