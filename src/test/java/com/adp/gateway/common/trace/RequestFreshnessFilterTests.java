package com.adp.gateway.common.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.adp.gateway.observability.GatewayObservability;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestFreshnessFilterTests {

    private static final Instant NOW = Instant.parse("2026-09-09T01:00:00Z");
    private final RequestFreshnessFilter filter = new RequestFreshnessFilter(
        new ObjectMapper().findAndRegisterModules(), Clock.fixed(NOW, ZoneOffset.UTC),
        true, Duration.ofMinutes(5), Duration.ofSeconds(30),
        new GatewayObservability(new SimpleMeterRegistry())
    );

    @Test
    void acceptsRuntimeSubmissionInsideReplayWindow() throws Exception {
        var request = runtimeRequest();
        request.addHeader(TraceHeaders.REQUEST_TIMESTAMP, NOW.minusSeconds(30).toString());
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    void rejectsMissingStaleAndFutureTimestamp() throws Exception {
        assertRejected(null);
        assertRejected(NOW.minus(Duration.ofMinutes(6)).toString());
        assertRejected(NOW.plus(Duration.ofMinutes(1)).toString());
        assertRejected("not-an-instant");
    }

    private void assertRejected(String timestamp) throws Exception {
        var request = runtimeRequest();
        if (timestamp != null) {
            request.addHeader(TraceHeaders.REQUEST_TIMESTAMP, timestamp);
        }
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getContentAsString()).contains("REQUEST_FRESHNESS_INVALID");
    }

    private MockHttpServletRequest runtimeRequest() {
        return new MockHttpServletRequest("POST", "/v1/runtime/executions");
    }
}
