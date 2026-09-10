#!/bin/sh
set -eu

GRADLE_BIN="${GRADLE_BIN:-gradle}"

"${GRADLE_BIN}" --no-daemon test \
  --tests 'com.adp.gateway.runtime.api.RuntimeExecutionControllerTests.rejectsInstitutionThatDoesNotMatchAuthenticatedPrincipalBeforeRetrieval' \
  --tests 'com.adp.gateway.runtime.api.RuntimeExecutionControllerTests.unknownDestinationProfileIsBlockedAndRecorded' \
  --tests 'com.adp.gateway.runtime.api.RuntimeExecutionControllerTests.sameIdempotencyKeyWithDifferentRequestIsRejectedAsConflict' \
  --tests 'com.adp.gateway.runtime.api.RuntimeExecutionControllerTests.sensitivePromptRequiresReviewAndNeverCreatesProviderRequest' \
  --tests 'com.adp.gateway.egress.application.OutboundGuardChainTests.rejectsSecretExactPayload' \
  --tests 'com.adp.gateway.egress.application.DestinationEndpointPolicyTests.rejectsPrivateLoopbackLinkLocalAndMetadataDestinations' \
  --tests 'com.adp.gateway.egress.infrastructure.ProjectProvisionalResponseGuardAdapterTests.rejectsSensitiveDataRegeneratedByProviderResponse' \
  --tests 'com.adp.gateway.recovery.application.ExternalInteractionRecoveryServiceTests.sentUnknownIsRescheduledWithoutBlindRetry' \
  --tests 'com.adp.gateway.digitalasset.DigitalAssetLocalProductE2ETests' \
  --tests 'com.adp.gateway.auditexport.api.AuditExportControllerTests'
