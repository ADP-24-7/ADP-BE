package com.adp.gateway.transform.infrastructure;

import java.time.Duration;
import java.util.Map;

import com.adp.gateway.retrieval.domain.DataClass;
import com.adp.gateway.transform.application.TransformInstruction;
import com.adp.gateway.transform.application.TransformResolutionContext;
import com.adp.gateway.transform.application.TransformStrategyResolver;
import com.adp.gateway.transform.domain.TransformStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.local-fixtures.enabled", havingValue = "true")
public class ProjectProvisionalTransformStrategyResolver implements TransformStrategyResolver {

    @Override
    public TransformInstruction resolve(TransformResolutionContext context) {
        TransformStrategy strategy = "tokenized_asset_purchase".equals(context.workloadId())
            ? digitalAssetStrategy(context)
            : switch (context.dataClass()) {
            case CUSTOMER_IDENTIFIER, ACCOUNT_IDENTIFIER -> TransformStrategy.VAULT_TOKEN;
            case TRANSACTION_IDENTIFIER -> TransformStrategy.HMAC_PSEUDO;
            case FINANCIAL_AMOUNT -> TransformStrategy.GENERALIZE;
            case FINANCIAL_METADATA, BUSINESS_METADATA -> TransformStrategy.KEEP;
            case UNKNOWN -> TransformStrategy.REMOVE;
        };
        return new TransformInstruction(
            strategy,
            "project-provisional-strategy-v1",
            "project-provisional-key-v1",
            "project-provisional-mapping-v1",
            Duration.ofHours(24),
            Map.of()
        );
    }

    private TransformStrategy digitalAssetStrategy(TransformResolutionContext context) {
        if (context.fieldPath().endsWith(".customerId") || context.fieldPath().endsWith(".accountId")) {
            return TransformStrategy.VAULT_TOKEN;
        }
        return switch (context.dataClass()) {
            case TRANSACTION_IDENTIFIER, FINANCIAL_AMOUNT, FINANCIAL_METADATA, BUSINESS_METADATA -> TransformStrategy.KEEP;
            case CUSTOMER_IDENTIFIER, ACCOUNT_IDENTIFIER -> TransformStrategy.VAULT_TOKEN;
            case UNKNOWN -> TransformStrategy.REMOVE;
        };
    }
}
