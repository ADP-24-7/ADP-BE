package com.adp.gateway.operations.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;

class InternalInfoControllerTests {

    @Test
    void returnsServiceMetadata() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC);
        Properties properties = new Properties();
        properties.setProperty("name", "adp-be");
        properties.setProperty("version", "0.0.1-SNAPSHOT");
        BuildProperties buildProperties = new BuildProperties(properties);
        InternalInfoController controller = new InternalInfoController(clock, buildProperties);

        ResponseEntity<Map<String, Object>> response = controller.info();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("service", "adp-be");
        assertThat(response.getBody()).containsEntry("version", "0.0.1-SNAPSHOT");
        assertThat(response.getBody()).containsEntry("timestamp", "2026-08-27T00:00Z");
    }
}
