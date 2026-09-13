package com.adp.gateway.digitalasset.infrastructure;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.local-dashboard-fixtures.enabled", havingValue = "true")
public class LocalDigitalAssetDashboardFixtureLoader implements ApplicationListener<ApplicationReadyEvent> {
    private final DataSource dataSource;

    public LocalDigitalAssetDashboardFixtureLoader(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        new ResourceDatabasePopulator(
            new ClassPathResource("db/local/V11__local_digital_asset_dashboard_activity.sql")
        ).execute(dataSource);
    }
}
