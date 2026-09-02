package com.adp.gateway.ai.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.context.domain.CanonicalContextField;
import com.adp.gateway.detection.application.SensitiveDataDetector;
import com.adp.gateway.retrieval.domain.DataClass;
import org.springframework.stereotype.Component;

@Component
public class AiCanonicalContextBuilder {

    private static final String PROMPT_PATH = "$.input.prompt";
    private static final Set<String> ALLOWED_INPUT_KEYS = Set.of("prompt");
    private static final int MAX_PROMPT_LENGTH = 4_000;

    private final CanonicalValueHasher hasher;
    private final SensitiveDataDetector sensitiveDataDetector;

    public AiCanonicalContextBuilder(CanonicalValueHasher hasher, SensitiveDataDetector sensitiveDataDetector) {
        this.hasher = hasher;
        this.sensitiveDataDetector = sensitiveDataDetector;
    }

    public CanonicalContext merge(CanonicalContext retrievalContext, Map<String, Object> input) {
        validate(input);
        String prompt = (String) input.get("prompt");
        CanonicalContext withPrompt = withPrompt(retrievalContext, prompt, DataClass.BUSINESS_METADATA);
        boolean sensitivePrompt = sensitiveDataDetector.detect(withPrompt).findings().stream()
            .anyMatch(finding -> PROMPT_PATH.equals(finding.contextPath()));
        return sensitivePrompt
            ? withPrompt(retrievalContext, prompt, DataClass.UNKNOWN)
            : withPrompt;
    }

    public void validate(Map<String, Object> input) {
        if (input == null || !ALLOWED_INPUT_KEYS.containsAll(input.keySet())) {
            throw new AiInputRejectedException("AI_INPUT_SCHEMA_MISMATCH");
        }
        Object promptValue = input.get("prompt");
        if (!(promptValue instanceof String prompt) || prompt.isBlank() || prompt.length() > MAX_PROMPT_LENGTH) {
            throw new AiInputRejectedException("AI_PROMPT_INVALID");
        }
    }

    private CanonicalContext withPrompt(CanonicalContext context, String prompt, DataClass dataClass) {
        List<CanonicalContextField> fields = new ArrayList<>(context.fields());
        fields.removeIf(field -> PROMPT_PATH.equals(field.path()));
        fields.add(new CanonicalContextField(
            PROMPT_PATH,
            "request",
            "prompt",
            dataClass,
            prompt,
            hasher.hash(PROMPT_PATH + ":" + dataClass.name() + ":" + prompt)
        ));
        fields.sort(Comparator.comparing(CanonicalContextField::path));
        String contextDigest = hasher.hash(fields.stream()
            .map(field -> field.path() + ":" + field.dataClass().name() + ":" + field.valueDigest())
            .collect(Collectors.joining("|")));
        return new CanonicalContext(
            context.schemaVersion(),
            context.contextId(),
            context.dataAccessId(),
            context.workloadId(),
            context.purpose(),
            context.subjectType(),
            context.subjectRefDigest(),
            List.copyOf(fields),
            contextDigest
        );
    }
}
