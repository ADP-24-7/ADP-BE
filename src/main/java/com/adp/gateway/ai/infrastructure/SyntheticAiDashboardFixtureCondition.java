package com.adp.gateway.ai.infrastructure;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

final class SyntheticAiDashboardFixtureCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return context.getEnvironment().getProperty("adp.local-dashboard-fixtures.enabled", Boolean.class, false)
            && "SYNTHETIC".equals(context.getEnvironment().getProperty("adp.environment.data-provenance", "NONE"));
    }
}
