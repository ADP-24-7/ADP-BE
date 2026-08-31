package com.adp.gateway.transform.application;

public interface TransformStrategyResolver {

    TransformInstruction resolve(TransformResolutionContext context);
}
