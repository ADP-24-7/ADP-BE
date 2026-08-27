package com.adp.gateway.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class HealthControllerTests {

    @Test
    void returnsServiceHealth() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC);
        HealthController controller = new HealthController(clock);

        ResponseEntity<Map<String, Object>> response = controller.health();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("status", "UP");
        assertThat(response.getBody()).containsEntry("service", "adp-be");
        assertThat(response.getBody()).containsEntry("timestamp", "2026-08-27T00:00Z");
    }
}
