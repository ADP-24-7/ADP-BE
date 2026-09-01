package com.adp.gateway.transform.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.adp.gateway.transform.application.PseudonymizationKeyPort;
import com.adp.gateway.transform.application.TransformStrategyResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TransformWiringTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(
            ProjectProvisionalTransformStrategyResolver.class,
            UnconfiguredTransformStrategyResolver.class,
            LocalInMemoryPseudonymizationKeyAdapter.class,
            UnconfiguredPseudonymizationKeyAdapter.class
        );

    @Test
    void defaultConfigUsesFailClosedTransformBeans() {
        contextRunner
            .withPropertyValues("adp.local-fixtures.enabled=false")
            .run(context -> {
                assertThat(context).hasSingleBean(TransformStrategyResolver.class);
                assertThat(context).hasSingleBean(PseudonymizationKeyPort.class);
                assertThat(context.getBean(TransformStrategyResolver.class))
                    .isInstanceOf(UnconfiguredTransformStrategyResolver.class);
                assertThat(context.getBean(PseudonymizationKeyPort.class))
                    .isInstanceOf(UnconfiguredPseudonymizationKeyAdapter.class);
            });
    }

    @Test
    void localFixturesUseProvisionalTransformBeans() {
        contextRunner
            .withPropertyValues("adp.local-fixtures.enabled=true")
            .run(context -> {
                assertThat(context).hasSingleBean(TransformStrategyResolver.class);
                assertThat(context).hasSingleBean(PseudonymizationKeyPort.class);
                assertThat(context.getBean(TransformStrategyResolver.class))
                    .isInstanceOf(ProjectProvisionalTransformStrategyResolver.class);
                assertThat(context.getBean(PseudonymizationKeyPort.class))
                    .isInstanceOf(LocalInMemoryPseudonymizationKeyAdapter.class);
            });
    }
}
