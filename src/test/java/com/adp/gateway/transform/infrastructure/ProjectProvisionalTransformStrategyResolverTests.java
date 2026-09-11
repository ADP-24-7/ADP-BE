package com.adp.gateway.transform.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.adp.gateway.retrieval.domain.DataClass;
import com.adp.gateway.transform.application.TransformResolutionContext;
import com.adp.gateway.transform.domain.TransformStrategy;
import org.junit.jupiter.api.Test;

class ProjectProvisionalTransformStrategyResolverTests {

    private final ProjectProvisionalTransformStrategyResolver resolver =
        new ProjectProvisionalTransformStrategyResolver();

    @Test
    void activatesE3TokenHmacAndRequiredExactSelectionsForCustomerSupport() {
        assertThat(resolve(DataClass.CUSTOMER_IDENTIFIER, "customer.customer_id")).isEqualTo(TransformStrategy.VAULT_TOKEN);
        assertThat(resolve(DataClass.ACCOUNT_IDENTIFIER, "account.account_id")).isEqualTo(TransformStrategy.VAULT_TOKEN);
        assertThat(resolve(DataClass.TRANSACTION_IDENTIFIER, "transaction.transaction_id")).isEqualTo(TransformStrategy.HMAC_PSEUDO);
        assertThat(resolve(DataClass.FINANCIAL_AMOUNT, "account.balance")).isEqualTo(TransformStrategy.KEEP);
        assertThat(resolve(DataClass.FINANCIAL_AMOUNT, "transaction.amount")).isEqualTo(TransformStrategy.KEEP);
    }

    @Test
    void doesNotGeneralizeTheE3SelectionToOtherWorkloads() {
        var instruction = resolver.resolve(new TransformResolutionContext(
            "another_workload", "ANOTHER_PURPOSE", "provider", "policy-v1", "snapshot",
            DataClass.FINANCIAL_AMOUNT, "account.balance"
        ));

        assertThat(instruction.strategy()).isEqualTo(TransformStrategy.GENERALIZE);
        assertThat(instruction.strategyVersion()).isEqualTo("project-provisional-strategy-v1");
    }

    private TransformStrategy resolve(DataClass dataClass, String path) {
        var instruction = resolver.resolve(new TransformResolutionContext(
            "customer_summary", "CUSTOMER_SUPPORT", "provider", "policy-v1", "snapshot", dataClass, path
        ));
        assertThat(instruction.strategyVersion()).isEqualTo("e3-transform-profile/1.2.0");
        return instruction.strategy();
    }
}
