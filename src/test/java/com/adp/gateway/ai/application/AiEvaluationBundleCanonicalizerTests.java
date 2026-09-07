package com.adp.gateway.ai.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AiEvaluationBundleCanonicalizerTests {

    @Test
    void digestIsIndependentOfInputMapOrdering() {
        ObjectMapper globalMapper = new ObjectMapper().findAndRegisterModules();
        AiEvaluationBundleCanonicalizer canonicalizer = new AiEvaluationBundleCanonicalizer(globalMapper);
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("zeta", Map.of("second", 2, "first", 1));
        first.put("alpha", null);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("alpha", null);
        second.put("zeta", Map.of("first", 1, "second", 2));

        assertThat(canonicalizer.digest(first)).isEqualTo(canonicalizer.digest(second));
    }
}
