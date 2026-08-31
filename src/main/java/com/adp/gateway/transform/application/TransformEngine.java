package com.adp.gateway.transform.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.Mac;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.context.domain.CanonicalContextField;
import com.adp.gateway.decision.domain.FinalAction;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.policy.domain.RuntimePolicyContext;
import com.adp.gateway.retrieval.domain.DataClass;
import com.adp.gateway.transform.domain.TransformFieldResult;
import com.adp.gateway.transform.domain.TransformResult;
import com.adp.gateway.transform.domain.TransformStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

@Service
public class TransformEngine {

    private final TransformStrategyResolver strategyResolver;
    private final VaultTokenPort vaultTokenPort;
    private final PseudonymizationKeyPort pseudonymizationKeyPort;
    private final CanonicalValueHasher hasher;
    private final MeterRegistry meterRegistry;

    public TransformEngine(
        TransformStrategyResolver strategyResolver,
        VaultTokenPort vaultTokenPort,
        PseudonymizationKeyPort pseudonymizationKeyPort,
        CanonicalValueHasher hasher,
        MeterRegistry meterRegistry
    ) {
        this.strategyResolver = strategyResolver;
        this.vaultTokenPort = vaultTokenPort;
        this.pseudonymizationKeyPort = pseudonymizationKeyPort;
        this.hasher = hasher;
        this.meterRegistry = meterRegistry;
    }

    public TransformResult transform(
        String executionId,
        CanonicalContext context,
        RuntimePolicyContext policyContext,
        RuntimeDecision decision
    ) {
        Timer.Sample timer = Timer.start(meterRegistry);
        String transformExecutionId = "trn_" + UUID.randomUUID();
        try {
            if (decision.finalAction() != FinalAction.TRANSFORM) {
                TransformResult result = TransformResult.skipped(transformExecutionId);
                recordExecutionMetric(result.status());
                return result;
            }

            List<TransformFieldResult> fields = new ArrayList<>();
            for (CanonicalContextField field : context.fields()) {
                TransformInstruction instruction = strategyResolver.resolve(resolutionContext(policyContext, decision, field));
                validateInstruction(instruction);
                Object transformedValue = transformedValue(decision, field, instruction);
                String instructionDigest = instructionDigest(instruction);
                String transformedDigest = transformedValue == null
                    ? null
                    : hasher.hash(field.path() + ":" + instruction.strategy().name() + ":" + transformedValue);
                fields.add(new TransformFieldResult(
                    field.path(),
                    field.datasetName(),
                    field.fieldName(),
                    field.dataClass(),
                    instruction.strategy(),
                    instruction.strategyVersion(),
                    instruction.keyVersion(),
                    instruction.mappingVersion(),
                    instructionDigest,
                    field.valueDigest(),
                    transformedDigest,
                    instruction.strategy() == TransformStrategy.VAULT_TOKEN ? String.valueOf(transformedValue) : null,
                    transformedValue
                ));
                recordStrategyMetric(field.dataClass(), instruction.strategy(), "SUCCESS");
            }
            fields.sort(Comparator.comparing(TransformFieldResult::path));
            String outputDigest = hasher.hash(fields.stream()
                .map(field -> field.path() + ":" + field.strategy().name() + ":" + value(field.transformedValueDigest()))
                .collect(Collectors.joining("|")));

            TransformResult result = new TransformResult(transformExecutionId, true, "APPLIED", outputDigest, fields);
            recordExecutionMetric(result.status());
            return result;
        } catch (RuntimeException exception) {
            recordExecutionMetric("FAILED");
            throw exception;
        } finally {
            timer.stop(Timer.builder("transform.execution.duration")
                .description("Transform engine execution duration")
                .register(meterRegistry));
        }
    }

    private TransformResolutionContext resolutionContext(
        RuntimePolicyContext policyContext,
        RuntimeDecision decision,
        CanonicalContextField field
    ) {
        return new TransformResolutionContext(
            policyContext.workloadId(),
            policyContext.purpose(),
            policyContext.provider(),
            decision.policyVersion(),
            decision.snapshotDigest(),
            field.dataClass(),
            field.path()
        );
    }

    private Object transformedValue(RuntimeDecision decision, CanonicalContextField field, TransformInstruction instruction) {
        Object value = field.value();
        return switch (instruction.strategy()) {
            case MASK -> mask(value, instruction);
            case HMAC_PSEUDO -> hmacPseudo(field.valueDigest(), instruction.keyVersion());
            case VAULT_TOKEN -> vaultTokenPort.tokenFor(new VaultTokenRequest(
                scope(decision, field.dataClass()),
                field.dataClass(),
                field.valueDigest(),
                instruction.keyVersion(),
                instruction.mappingVersion(),
                instruction.tokenTtl()
            ));
            case REMOVE -> null;
            case KEEP -> value;
            case GENERALIZE -> generalize(value, instruction);
            case FIELD_SEPARATION -> hasher.hash("FIELD_SEPARATION:" + field.valueDigest());
        };
    }

    private void validateInstruction(TransformInstruction instruction) {
        requireText(instruction.strategyVersion(), "strategyVersion");
        requireText(instruction.keyVersion(), "keyVersion");
        requireText(instruction.mappingVersion(), "mappingVersion");
        switch (instruction.strategy()) {
            case MASK -> {
                int visibleSuffix = intParameter(instruction.parameters(), "visibleSuffix", 4);
                if (visibleSuffix < 0) {
                    throw new TransformResolutionException("MASK visibleSuffix must be greater than or equal to 0");
                }
            }
            case GENERALIZE -> {
                int bucketSize = intParameter(instruction.parameters(), "bucketSize", 1000);
                if (bucketSize <= 0) {
                    throw new TransformResolutionException("GENERALIZE bucketSize must be greater than 0");
                }
            }
            case VAULT_TOKEN -> {
                if (instruction.tokenTtl() == null || instruction.tokenTtl().isZero() || instruction.tokenTtl().isNegative()) {
                    throw new TransformResolutionException("VAULT_TOKEN tokenTtl must be greater than 0");
                }
            }
            case HMAC_PSEUDO, REMOVE, KEEP, FIELD_SEPARATION -> {
            }
        }
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new TransformResolutionException(name + " must not be blank");
        }
    }

    private String mask(Object value, TransformInstruction instruction) {
        String raw = String.valueOf(value);
        int visibleSuffix = intParameter(instruction.parameters(), "visibleSuffix", 4);
        if (visibleSuffix <= 0) {
            return "*".repeat(raw.length());
        }
        if (raw.length() <= visibleSuffix) {
            return "*".repeat(raw.length());
        }
        return "*".repeat(raw.length() - visibleSuffix) + raw.substring(raw.length() - visibleSuffix);
    }

    private String hmacPseudo(String valueDigest, String keyVersion) {
        try {
            PseudonymizationKey key = pseudonymizationKeyPort.load(keyVersion);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key.secretKey());
            return HexFormat.of().formatHex(mac.doFinal(valueDigest.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC pseudonymization failed", exception);
        }
    }

    private Object generalize(Object value, TransformInstruction instruction) {
        int bucketSize = intParameter(instruction.parameters(), "bucketSize", 1000);
        try {
            BigDecimal amount = new BigDecimal(String.valueOf(value));
            BigDecimal bucket = amount.divideToIntegralValue(BigDecimal.valueOf(bucketSize)).multiply(BigDecimal.valueOf(bucketSize));
            return bucket.toPlainString() + "+";
        } catch (NumberFormatException exception) {
            return "<generalized>";
        }
    }

    private String scope(RuntimeDecision decision, DataClass dataClass) {
        return decision.snapshotDigest() + ":" + dataClass.name();
    }

    private String value(String value) {
        return value == null ? "<removed>" : value;
    }

    private String instructionDigest(TransformInstruction instruction) {
        return hasher.hash(String.join("|",
            instruction.strategy().name(),
            value(instruction.strategyVersion()),
            value(instruction.keyVersion()),
            value(instruction.mappingVersion()),
            instruction.tokenTtl() == null ? "<none>" : String.valueOf(instruction.tokenTtl().toSeconds()),
            canonicalParameters(instruction.parameters())
        ));
    }

    private String canonicalParameters(Map<String, String> parameters) {
        return parameters.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining("&"));
    }

    private int intParameter(Map<String, String> parameters, String name, int defaultValue) {
        String value = parameters.get(name);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new TransformResolutionException(name + " must be an integer");
        }
    }

    private void recordExecutionMetric(String result) {
        meterRegistry.counter("transform.execution.total", "result", result).increment();
        if ("FAILED".equals(result)) {
            meterRegistry.counter("transform.execution.failed.total").increment();
        }
    }

    private void recordStrategyMetric(DataClass dataClass, TransformStrategy strategy, String result) {
        meterRegistry.counter(
            "transform.strategy.total",
            "strategy", strategy.name(),
            "data_class", dataClass.name(),
            "result", result
        ).increment();
    }
}
