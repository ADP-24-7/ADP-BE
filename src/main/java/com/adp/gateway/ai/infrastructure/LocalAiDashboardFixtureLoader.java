package com.adp.gateway.ai.infrastructure;

import javax.sql.DataSource;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

@Component
@Conditional(SyntheticAiDashboardFixtureCondition.class)
public class LocalAiDashboardFixtureLoader implements ApplicationListener<ApplicationReadyEvent> {
    private final DataSource dataSource;

    public LocalAiDashboardFixtureLoader(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        new ResourceDatabasePopulator(
            new ClassPathResource("db/local/V12__local_ai_dashboard_activity.sql")
        ).execute(dataSource);
    }
}
