package com.adp.gateway.digitalasset.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class DigitalAssetCanonicalJson {
    private final ObjectMapper mapper = new ObjectMapper()
        .setDefaultPropertyInclusion(JsonInclude.Value.construct(
            JsonInclude.Include.ALWAYS, JsonInclude.Include.ALWAYS
        ));

    public String serialize(Object value) {
        try {
            return mapper.writeValueAsString(sort(mapper.valueToTree(value)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to canonicalize Digital Asset contract", exception);
        }
    }

    public String digest(Object value) {
        try {
            byte[] canonical = serialize(value).getBytes(StandardCharsets.UTF_8);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
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
