package com.adp.gateway.transform.infrastructure;

import com.adp.gateway.retrieval.domain.DataClass;
import com.adp.gateway.transform.application.TransformStrategyResolver;
import com.adp.gateway.transform.domain.TransformStrategy;
import org.springframework.stereotype.Component;

@Component
public class ProjectProvisionalTransformStrategyResolver implements TransformStrategyResolver {

    @Override
    public TransformStrategy resolve(DataClass dataClass) {
        return switch (dataClass) {
            case CUSTOMER_IDENTIFIER, ACCOUNT_IDENTIFIER -> TransformStrategy.VAULT_TOKEN;
            case TRANSACTION_IDENTIFIER -> TransformStrategy.HMAC_PSEUDO;
            case FINANCIAL_AMOUNT -> TransformStrategy.GENERALIZE;
            case FINANCIAL_METADATA, BUSINESS_METADATA -> TransformStrategy.KEEP;
            case UNKNOWN -> TransformStrategy.REMOVE;
        };
    }
}
