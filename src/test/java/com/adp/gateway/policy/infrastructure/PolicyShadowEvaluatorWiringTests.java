package com.adp.gateway.policy.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.policy.application.PolicyShadowEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PolicyShadowEvaluatorWiringTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withBean(CanonicalValueHasher.class)
        .withUserConfiguration(
            ProjectProvisionalPolicyShadowEvaluator.class,
            UnconfiguredPolicyShadowEvaluator.class
        );

    @Test
    void usesFailClosedEvaluatorByDefault() {
        contextRunner.withPropertyValues("adp.local-fixtures.enabled=false").run(context -> {
            assertThat(context).hasSingleBean(PolicyShadowEvaluator.class);
            assertThat(context.getBean(PolicyShadowEvaluator.class))
                .isInstanceOf(UnconfiguredPolicyShadowEvaluator.class);
        });
    }

    @Test
    void usesProjectFixtureEvaluatorOnlyWhenLocalFixturesAreEnabled() {
        contextRunner.withPropertyValues("adp.local-fixtures.enabled=true")
            .run(context -> {
                assertThat(context).hasSingleBean(PolicyShadowEvaluator.class);
                assertThat(context.getBean(PolicyShadowEvaluator.class))
                    .isInstanceOf(ProjectProvisionalPolicyShadowEvaluator.class);
            });
    }
}
