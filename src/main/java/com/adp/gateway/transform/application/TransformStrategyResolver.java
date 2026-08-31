package com.adp.gateway.transform.application;

import com.adp.gateway.retrieval.domain.DataClass;
import com.adp.gateway.transform.domain.TransformStrategy;

public interface TransformStrategyResolver {

    TransformStrategy resolve(DataClass dataClass);
}
