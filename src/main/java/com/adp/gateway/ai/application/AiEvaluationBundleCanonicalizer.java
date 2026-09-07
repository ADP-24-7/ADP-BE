package com.adp.gateway.ai.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class AiEvaluationBundleCanonicalizer {
    private final ObjectMapper canonicalMapper;

    public AiEvaluationBundleCanonicalizer(ObjectMapper objectMapper) {
        this.canonicalMapper = objectMapper.copy()
            .setDefaultPropertyInclusion(JsonInclude.Value.construct(
                JsonInclude.Include.ALWAYS, JsonInclude.Include.ALWAYS
            ))
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    public String digest(Object value) {
        try {
            byte[] canonical = canonicalMapper.writeValueAsBytes(sort(canonicalMapper.valueToTree(value)));
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to canonicalize AI evaluation bundle", exception);
        }
    }

    private JsonNode sort(JsonNode node) {
        if (node.isObject()) {
            var fields = new TreeMap<String, JsonNode>();
            node.properties().forEach(entry -> fields.put(entry.getKey(), sort(entry.getValue())));
            ObjectNode sorted = canonicalMapper.createObjectNode();
            fields.forEach(sorted::set);
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode sorted = canonicalMapper.createArrayNode();
            node.forEach(item -> sorted.add(sort(item)));
            return sorted;
        }
        return node;
    }
}
