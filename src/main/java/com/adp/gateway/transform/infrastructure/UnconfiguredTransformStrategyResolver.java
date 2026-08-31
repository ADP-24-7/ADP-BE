package com.adp.gateway.transform.infrastructure;

import com.adp.gateway.transform.application.TransformInstruction;
import com.adp.gateway.transform.application.TransformResolutionContext;
import com.adp.gateway.transform.application.TransformResolutionException;
import com.adp.gateway.transform.application.TransformStrategyResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.local-fixtures.enabled", havingValue = "false", matchIfMissing = true)
public class UnconfiguredTransformStrategyResolver implements TransformStrategyResolver {

    @Override
    public TransformInstruction resolve(TransformResolutionContext context) {
        throw new TransformResolutionException("Transform strategy mapping is not configured");
    }
}
