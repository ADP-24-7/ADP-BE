package com.adp.gateway.policy.domain;

public interface PolicySnapshotPort {

    PolicySnapshot load(String workloadId);
}
