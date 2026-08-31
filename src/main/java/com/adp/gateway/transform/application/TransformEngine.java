package com.adp.gateway.transform.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.context.domain.CanonicalContextField;
import com.adp.gateway.decision.domain.FinalAction;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.retrieval.domain.DataClass;
import com.adp.gateway.transform.domain.TransformFieldResult;
import com.adp.gateway.transform.domain.TransformResult;
import com.adp.gateway.transform.domain.TransformStrategy;
import org.springframework.stereotype.Service;

@Service
public class TransformEngine {

    private final TransformStrategyResolver strategyResolver;
    private final VaultTokenPort vaultTokenPort;
    private final CanonicalValueHasher hasher;

    public TransformEngine(
        TransformStrategyResolver strategyResolver,
        VaultTokenPort vaultTokenPort,
        CanonicalValueHasher hasher
    ) {
        this.strategyResolver = strategyResolver;
        this.vaultTokenPort = vaultTokenPort;
        this.hasher = hasher;
    }

    public TransformResult transform(
        String executionId,
        CanonicalContext context,
        RuntimeDecision decision
    ) {
        String transformExecutionId = "trn_" + UUID.randomUUID();
        if (decision.finalAction() != FinalAction.TRANSFORM) {
            return TransformResult.skipped(transformExecutionId);
        }

        List<TransformFieldResult> fields = new ArrayList<>();
        for (CanonicalContextField field : context.fields()) {
            TransformStrategy strategy = strategyResolver.resolve(field.dataClass());
            Object transformedValue = transformedValue(decision, field, strategy);
            String transformedDigest = transformedValue == null
                ? null
                : hasher.hash(field.path() + ":" + strategy.name() + ":" + transformedValue);
            fields.add(new TransformFieldResult(
                field.path(),
                field.datasetName(),
                field.fieldName(),
                field.dataClass(),
                strategy,
                field.valueDigest(),
                transformedDigest,
                strategy == TransformStrategy.VAULT_TOKEN ? String.valueOf(transformedValue) : null,
                transformedValue
            ));
        }
        fields.sort(Comparator.comparing(TransformFieldResult::path));
        String outputDigest = hasher.hash(fields.stream()
            .map(field -> field.path() + ":" + field.strategy().name() + ":" + value(field.transformedValueDigest()))
            .collect(Collectors.joining("|")));

        return new TransformResult(transformExecutionId, true, "APPLIED", outputDigest, fields);
    }

    private Object transformedValue(RuntimeDecision decision, CanonicalContextField field, TransformStrategy strategy) {
        Object value = field.value();
        return switch (strategy) {
            case MASK -> mask(value);
            case HMAC_PSEUDO -> hasher.hash("HMAC_PSEUDO:" + field.valueDigest());
            case VAULT_TOKEN -> vaultTokenPort.tokenFor(scope(decision, field.dataClass()), field.dataClass(), field.valueDigest());
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
}
