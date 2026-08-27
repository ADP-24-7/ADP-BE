package com.adp.gateway.auth.infrastructure;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.local-fixtures.enabled", havingValue = "true")
public class LocalAuthFixtureLoader implements ApplicationListener<ApplicationReadyEvent> {

    private final DataSource dataSource;

    public LocalAuthFixtureLoader(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
            new ClassPathResource("db/local/V1__local_auth_fixture.sql"),
            new ClassPathResource("db/local/V2__local_data_access_fixture.sql")
        );
        populator.execute(dataSource);
    }
}
