package com.adp.gateway.workload.domain;

import java.util.Optional;

public interface WorkloadRegistryPort {

    Optional<WorkloadDefinition> findEnabled(String workloadId);
}
