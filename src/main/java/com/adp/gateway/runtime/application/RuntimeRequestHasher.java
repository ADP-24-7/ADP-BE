package com.adp.gateway.runtime.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

@Component
public class RuntimeRequestHasher {

    private final ObjectMapper objectMapper;

    public RuntimeRequestHasher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public String hash(
        String institutionId,
        String approvalReference,
        String workloadId,
        String purposeCode,
        String subjectScope,
        String destinationProfileId,
        List<String> processingContexts,
        Map<String, Object> input
    ) {
        Map<String, Object> canonical = new TreeMap<>();
        canonical.put("approvalReference", approvalReference);
        canonical.put("destinationProfileId", destinationProfileId);
        canonical.put("input", input == null ? Map.of() : input);
        canonical.put("institutionId", institutionId);
        canonical.put("processingContexts", processingContexts == null
            ? List.of()
            : processingContexts.stream().distinct().sorted().toList());
        canonical.put("purposeCode", purposeCode);
        canonical.put("subjectScope", subjectScope);
        canonical.put("workloadId", workloadId);
        try {
            String json = objectMapper.writeValueAsString(canonical);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Runtime request must be JSON serializable", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }
}
