package com.adp.gateway.transform.infrastructure;

import com.adp.gateway.transform.application.TransformInstruction;
import com.adp.gateway.transform.application.TransformResolutionContext;
import com.adp.gateway.transform.application.TransformResolutionException;
import com.adp.gateway.transform.application.TransformStrategyResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(TransformStrategyResolver.class)
public class UnconfiguredTransformStrategyResolver implements TransformStrategyResolver {

    @Override
    public TransformInstruction resolve(TransformResolutionContext context) {
        throw new TransformResolutionException("Transform strategy mapping is not configured");
    }
}
