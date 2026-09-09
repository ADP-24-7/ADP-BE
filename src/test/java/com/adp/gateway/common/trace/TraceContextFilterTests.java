package com.adp.gateway.common.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceContextFilterTests {

    @Test
    void generatesAuthoritativeServerTraceAndKeepsOnlyCallerTraceDigest() throws Exception {
        var hasher = new CanonicalValueHasher();
        var filter = new TraceContextFilter(new ObjectMapper(), Clock.systemUTC(), hasher, false);
        var request = new MockHttpServletRequest("POST", "/v1/runtime/executions");
        request.addHeader(TraceHeaders.TRACE_ID, "caller-trace-123");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String serverTrace = response.getHeader(TraceHeaders.TRACE_ID);
        assertThat(serverTrace).isNotBlank().isNotEqualTo("caller-trace-123");
        assertThat(request.getAttribute(TraceHeaders.TRACE_ID_ATTRIBUTE)).isEqualTo(serverTrace);
        assertThat(request.getAttribute(TraceHeaders.CLIENT_TRACE_ID_DIGEST_ATTRIBUTE))
            .isEqualTo(hasher.hash("caller-trace-123"));
    }
}
