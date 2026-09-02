package com.adp.gateway.policyharness.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.adp.gateway.dataaccess.application.SubjectRefHasher;
import com.adp.gateway.policyharness.application.ApprovalScopePort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ApprovalScopeWiringTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(
            SubjectRefHasher.class,
            ProjectProvisionalApprovalScopeAdapter.class,
            UnconfiguredApprovalScopeAdapter.class
        );

    @Test
    void defaultConfigUsesFailClosedApprovalScopeAdapter() {
        contextRunner
            .withPropertyValues("adp.local-fixtures.enabled=false")
            .run(context -> {
                assertThat(context).hasSingleBean(ApprovalScopePort.class);
                assertThat(context.getBean(ApprovalScopePort.class))
                    .isInstanceOf(UnconfiguredApprovalScopeAdapter.class);
            });
    }

    @Test
    void localFixturesUseProjectProvisionalApprovalScopeAdapter() {
        contextRunner
            .withPropertyValues("adp.local-fixtures.enabled=true")
            .run(context -> {
                assertThat(context).hasSingleBean(ApprovalScopePort.class);
                assertThat(context.getBean(ApprovalScopePort.class))
                    .isInstanceOf(ProjectProvisionalApprovalScopeAdapter.class);
            });
    }
}
