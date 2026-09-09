package com.adp.gateway.ai.application;

import java.util.Map;
import java.util.List;

/** Shared by the run catalog, contract freezer and actual provider mapper. */
public final class AiEvaluationPrompt {
    public static final String VERSION = "customer-summary-prompt/1.0.0";
    public static final String TEXT = "승인된 고객 정보를 간단히 요약하세요";
    public static Map<String, Object> snapshot() {
        return Map.of("version", VERSION, "input_prompt", TEXT, "system_prompt_mode", "ABSENT",
            "message_role", "user", "template", "canonical-json-of-approved-transformed-fields/v1");
    }
    public static List<Map<String, String>> messages(String canonicalFields) {
        return List.of(Map.of("role", "user", "content", canonicalFields));
    }
    private AiEvaluationPrompt() { }
}
