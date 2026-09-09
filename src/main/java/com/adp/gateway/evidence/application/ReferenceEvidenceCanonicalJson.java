package com.adp.gateway.evidence.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class ReferenceEvidenceCanonicalJson {
    private final ObjectMapper mapper;

    public ReferenceEvidenceCanonicalJson(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String digest(JsonNode value) {
        try {
            String canonical = mapper.writeValueAsString(sort(value));
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to canonicalize Reference Evidence", exception);
        }
    }

    private JsonNode sort(JsonNode node) {
        if (node.isObject()) {
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            node.properties().forEach(entry -> fields.put(entry.getKey(), sort(entry.getValue())));
            ObjectNode sorted = mapper.createObjectNode();
            fields.forEach(sorted::set);
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode result = mapper.createArrayNode();
            node.forEach(item -> result.add(sort(item)));
            return result;
        }
        return node;
    }
}
