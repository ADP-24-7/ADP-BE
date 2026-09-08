package com.adp.gateway.digitalasset.application;

import java.util.Optional;

import com.adp.gateway.digitalasset.domain.ExternalExecutionResult;

public interface InternalTraceResolver {
    Optional<String> resolveEvidenceDigest(ExternalExecutionResult result);
}
