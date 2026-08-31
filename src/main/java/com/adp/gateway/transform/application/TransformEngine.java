package com.adp.gateway.transform.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
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
                Object transformedValue = transformedValue(decision, field, instruction);
                String transformedDigest = transformedValue == null
                    ? null
                    : hasher.hash(field.path() + ":" + instruction.strategy().name() + ":" + transformedValue);
                fields.add(new TransformFieldResult(
                    field.path(),
                    field.datasetName(),
                    field.fieldName(),
                    field.dataClass(),
                    instruction.strategy(),
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
            case MASK -> mask(value);
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
            case GENERALIZE -> generalize(value);
            case FIELD_SEPARATION -> hasher.hash("FIELD_SEPARATION:" + field.valueDigest());
        };
    }

    private String mask(Object value) {
        String raw = String.valueOf(value);
        if (raw.length() <= 4) {
            return "*".repeat(raw.length());
        }
        return "*".repeat(raw.length() - 4) + raw.substring(raw.length() - 4);
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

    private Object generalize(Object value) {
        try {
            BigDecimal amount = new BigDecimal(String.valueOf(value));
            BigDecimal bucket = amount.divideToIntegralValue(BigDecimal.valueOf(1000)).multiply(BigDecimal.valueOf(1000));
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
