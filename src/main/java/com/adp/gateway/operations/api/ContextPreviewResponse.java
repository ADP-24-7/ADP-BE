package com.adp.gateway.operations.api;

import java.util.List;

import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.detection.domain.DetectionResult;

public record ContextPreviewResponse(
    String schemaVersion,
    String contextId,
    String dataAccessId,
    String workloadId,
    String purpose,
    String subjectType,
    String subjectRefDigest,
    String contextDigest,
    List<FieldResponse> fields,
    DetectionResponse detection
) {

    public static ContextPreviewResponse from(CanonicalContext context, DetectionResult detectionResult) {
        return new ContextPreviewResponse(
            context.schemaVersion(),
            context.contextId(),
            context.dataAccessId(),
            context.workloadId(),
            context.purpose(),
            context.subjectType(),
            context.subjectRefDigest(),
            context.contextDigest(),
            context.fields().stream()
                .map(field -> new FieldResponse(
                    field.path(),
                    field.datasetName(),
                    field.fieldName(),
                    field.dataClass().name(),
                    field.valueDigest(),
                    field.hasUnknownDataClass()
                ))
                .toList(),
            new DetectionResponse(
                detectionResult.detectorVersion(),
                detectionResult.findings().stream()
                    .map(finding -> new FindingResponse(
                        finding.type().name(),
                        finding.contextPath(),
                        finding.startOffset(),
                        finding.endOffset(),
                        finding.detectorVersion(),
                        finding.evidenceDigest()
                    ))
                    .toList()
            )
        );
    }

    public record FieldResponse(
        String path,
        String datasetName,
        String fieldName,
        String dataClass,
        String valueDigest,
        boolean unknownDataClass
    ) {
    }

    public record DetectionResponse(
        String detectorVersion,
        List<FindingResponse> findings
    ) {
    }

    public record FindingResponse(
        String type,
        String contextPath,
        int startOffset,
        int endOffset,
        String detectorVersion,
        String evidenceDigest
    ) {
    }
}
