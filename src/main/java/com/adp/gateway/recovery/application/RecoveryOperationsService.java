package com.adp.gateway.recovery.application;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.recovery.domain.RecoveryCommandResult;
import com.adp.gateway.recovery.domain.RecoveryIncidentDetail;
import com.adp.gateway.recovery.domain.RecoveryIncidentPage;
import com.adp.gateway.recovery.domain.RecoveryOperationEvent;
import com.adp.gateway.recovery.domain.RecoveryOperationOutcome;
import com.adp.gateway.recovery.domain.RecoveryOperationType;
import com.adp.gateway.recovery.domain.RecoveryStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class RecoveryOperationsService {

    private final RecoveryOperationsPersistence operations;
    private final ExternalInteractionRecoveryPersistence recoveryPersistence;
    private final ExternalInteractionRecoveryService recoveryService;
    private final Clock clock;
    private final Duration leaseDuration;

    public RecoveryOperationsService(
        RecoveryOperationsPersistence operations,
        ExternalInteractionRecoveryPersistence recoveryPersistence,
        ExternalInteractionRecoveryService recoveryService,
        Clock clock,
        @Value("${adp.recovery.lease-duration:30s}") Duration leaseDuration
    ) {
        this.operations = operations;
        this.recoveryPersistence = recoveryPersistence;
        this.recoveryService = recoveryService;
        this.clock = clock;
        this.leaseDuration = leaseDuration;
    }

    public RecoveryIncidentPage search(
        AuthPrincipal principal,
        RecoveryStatus status,
        int page,
        int size
    ) {
        requireReader(principal);
        return operations.search(principal.institutionId(), principal.workloadIds(), status, page, size);
    }

    public RecoveryIncidentDetail load(AuthPrincipal principal, String recoveryId) {
        requireReader(principal);
        return operations.load(recoveryId, principal.institutionId(), principal.workloadIds());
    }

    public RecoveryCommandResult command(
        AuthPrincipal principal,
        String recoveryId,
        String operationId,
        RecoveryOperationType type
    ) {
        requirePrivileged(principal);
        validateIdentifier(recoveryId, "RECOVERY_INCIDENT_NOT_FOUND");
        validateIdentifier(operationId, "RECOVERY_COMMAND_INVALID");
        OffsetDateTime now = OffsetDateTime.now(clock);
        var existing = operations.findOperation(
            recoveryId, operationId, principal.institutionId(), principal.workloadIds()
        );
        if (existing.isPresent()) {
            return replay(existing.get(), recoveryId, type, principal);
        }
        boolean reserved = operations.reserveOperation(
            recoveryId, operationId, principal.institutionId(), principal.workloadIds(),
            principal.principalId(), type, now
        );
        if (!reserved) {
            return operations.findOperation(
                    recoveryId, operationId, principal.institutionId(), principal.workloadIds()
                )
                .map(event -> replay(event, recoveryId, type, principal))
                .orElseThrow(() -> new RecoveryOperationException("RECOVERY_INCIDENT_NOT_FOUND"));
        }

        String workerId = "manual:" + principal.principalId() + ":" + operationId;
        try {
            var recovery = (type == RecoveryOperationType.MARK_REVIEW
                ? recoveryPersistence.claimForManualReview(
                    recoveryId, workerId, principal.institutionId(), principal.workloadIds(), now, leaseDuration
                )
                : recoveryPersistence.claimById(
                    recoveryId, workerId, principal.institutionId(), principal.workloadIds(), now, leaseDuration
                )).orElseThrow(() -> new RecoveryOperationException("RECOVERY_COMMAND_NOT_ALLOWED"));
            RecoveryProcessingResult processed = switch (type) {
                case RECONCILE -> recoveryService.processClaimed(recovery, workerId, false);
                case RETRY -> recoveryService.processClaimed(recovery, workerId, true);
                case MARK_REVIEW -> recoveryService.markClaimedManualReview(recovery, workerId);
            };
            operations.completeOperation(
                recoveryId, operationId, RecoveryOperationOutcome.SUCCEEDED,
                processed.reasonCode(), processed.evidenceDigest(), OffsetDateTime.now(clock)
            );
            return new RecoveryCommandResult(
                recoveryId, operationId, type, RecoveryOperationOutcome.SUCCEEDED,
                processed.recoveryStatus(), processed.reasonCode(), false
            );
        } catch (RecoveryOperationException | StaleRecoveryLeaseException exception) {
            completeRejected(recoveryId, operationId, exceptionReason(exception));
            throw exception;
        } catch (RuntimeException exception) {
            operations.completeOperation(
                recoveryId, operationId, RecoveryOperationOutcome.FAILED,
                "RECOVERY_COMMAND_FAILED", null, OffsetDateTime.now(clock)
            );
            throw exception;
        }
    }

    private RecoveryCommandResult replay(
        RecoveryOperationEvent event,
        String recoveryId,
        RecoveryOperationType requestedType,
        AuthPrincipal principal
    ) {
        if (event.operationType() != requestedType) {
            throw new RecoveryOperationException("RECOVERY_COMMAND_CONFLICT");
        }
        if (event.outcome() == RecoveryOperationOutcome.IN_PROGRESS) {
            throw new RecoveryOperationException("RECOVERY_COMMAND_IN_PROGRESS");
        }
        RecoveryIncidentDetail incident = operations.load(
            recoveryId, principal.institutionId(), principal.workloadIds()
        );
        return new RecoveryCommandResult(
            recoveryId, event.operationId(), event.operationType(), event.outcome(),
            incident.recoveryStatus(), event.reasonCode(), true
        );
    }

    private void completeRejected(String recoveryId, String operationId, String reasonCode) {
        operations.completeOperation(
            recoveryId, operationId, RecoveryOperationOutcome.REJECTED,
            reasonCode, null, OffsetDateTime.now(clock)
        );
    }

    private String exceptionReason(RuntimeException exception) {
        return exception instanceof RecoveryOperationException operationException
            ? operationException.reasonCode() : "RECOVERY_STALE_LEASE";
    }

    private void requireReader(AuthPrincipal principal) {
        requireInstitution(principal);
        if (!principal.hasRole(AdpRole.OPERATOR) && !principal.hasRole(AdpRole.PRIVILEGED_OPERATOR)
            && !principal.hasRole(AdpRole.AUDITOR)) {
            throw new AccessDeniedException("Recovery incident access is forbidden");
        }
    }

    private void requirePrivileged(AuthPrincipal principal) {
        requireInstitution(principal);
        if (!principal.hasRole(AdpRole.PRIVILEGED_OPERATOR)) {
            throw new AccessDeniedException("Recovery command requires privileged operator role");
        }
    }

    private void requireInstitution(AuthPrincipal principal) {
        if (principal == null || principal.institutionId() == null || principal.institutionId().isBlank()) {
            throw new AccessDeniedException("Recovery institution scope is required");
        }
    }

    private void validateIdentifier(String value, String reasonCode) {
        if (value == null || !value.matches("[A-Za-z0-9:_-]{1,120}")) {
            throw new RecoveryOperationException(reasonCode);
        }
    }
}
