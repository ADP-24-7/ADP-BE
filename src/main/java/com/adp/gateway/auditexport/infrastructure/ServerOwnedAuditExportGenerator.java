package com.adp.gateway.auditexport.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import com.adp.gateway.audit.domain.ExecutionEvidencePack;
import com.adp.gateway.auditexport.application.AuditExportException;
import com.adp.gateway.auditexport.application.AuditExportGenerator;
import com.adp.gateway.auditexport.application.GeneratedAuditExport;
import com.adp.gateway.auditexport.domain.AuditExportFormat;
import com.adp.gateway.auditexport.domain.AuditExportJob;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ServerOwnedAuditExportGenerator implements AuditExportGenerator {
    private static final String CLASSIFICATION = "INTERNAL-PRIVACY-SAFE-EVIDENCE";
    private static final List<String> CSV_HEADERS = List.of(
        "schema_version", "classification", "generated_by", "execution_id", "trace_id", "audit_id",
        "created_at", "updated_at", "institution_id", "workload_id", "execution_pack", "purpose_code",
        "runtime_status", "final_action", "reason_codes", "policy_version", "snapshot_digest",
        "destination_profile_id", "destination_profile_version", "connector_status", "response_guard_status",
        "recovery_status", "last_observed_external_status", "evidence_digest"
    );

    private final Clock clock;
    private final int maxBytes;

    public ServerOwnedAuditExportGenerator(
        Clock clock,
        @Value("${adp.audit-export.max-file-size:2MB}") org.springframework.util.unit.DataSize maxFileSize
    ) {
        this.clock = clock;
        this.maxBytes = Math.toIntExact(maxFileSize.toBytes());
    }

    @Override
    public GeneratedAuditExport generate(
        AuditExportJob job,
        ExecutionEvidencePack evidence
    ) {
        OffsetDateTime generatedAt = OffsetDateTime.now(clock);
        byte[] content = job.format() == AuditExportFormat.CSV
            ? csv(job, evidence)
            : pdf(job, evidence, generatedAt);
        if (content.length > maxBytes) {
            throw new AuditExportException("AUDIT_EXPORT_SIZE_LIMIT_EXCEEDED", "Generated export is too large");
        }
        String suffix = job.format().name().toLowerCase(java.util.Locale.ROOT);
        return new GeneratedAuditExport(
            content,
            sha256(content),
            job.format() == AuditExportFormat.CSV ? "text/csv;charset=UTF-8" : "application/pdf",
            "adp-evidence-" + safeFilePart(evidence.executionId()) + "." + suffix,
            1
        );
    }

    private byte[] csv(AuditExportJob job, ExecutionEvidencePack evidence) {
        List<String> values = List.of(
            "adp-audit-export/v1", CLASSIFICATION, job.requesterId(), evidence.executionId(), evidence.traceId(),
            nullable(evidence.audit().auditId()), evidence.createdAt().toString(), evidence.updatedAt().toString(),
            evidence.institutionId(), evidence.workloadId(), job.executionPack().name(), evidence.purposeCode(),
            evidence.runtimeStatus(), nullable(evidence.policy().finalAction()), nullable(evidence.audit().reasonCode()),
            nullable(evidence.policy().policyVersion()), nullable(evidence.policy().snapshotDigest()),
            nullable(evidence.egress().destinationProfileId()), nullable(evidence.egress().destinationProfileVersion()),
            nullable(evidence.egress().connectorStatus()), nullable(evidence.egress().responseGuardStatus()),
            nullable(evidence.recovery().recoveryStatus()), nullable(evidence.recovery().lastObservedExternalStatus()),
            evidence.exportContentDigest()
        );
        String document = csvRow(CSV_HEADERS) + "\r\n" + csvRow(values) + "\r\n";
        byte[] body = document.getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        return withBom;
    }

    private byte[] pdf(AuditExportJob job, ExecutionEvidencePack evidence, OffsetDateTime generatedAt) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                float y = 790;
                y = line(stream, bold, 16, 48, y, "ADP Execution Evidence Report");
                y = line(stream, regular, 9, 48, y - 4, "Classification: " + CLASSIFICATION);
                y = line(stream, regular, 9, 48, y, "Schema: adp-audit-export/v1");
                y = line(stream, regular, 9, 48, y, "Generated by: " + job.requesterId());
                y = line(stream, regular, 9, 48, y, "Generated at: " + generatedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                y -= 12;
                for (String value : pdfLines(job, evidence)) {
                    y = line(stream, regular, 9, 48, y, value);
                }
                y -= 8;
                line(stream, regular, 8, 48, y,
                    "This report contains bounded metadata and digests only. Raw prompts, payloads, PII, secrets, and token maps are excluded.");
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new AuditExportException("AUDIT_EXPORT_GENERATION_FAILED", "Unable to render PDF");
        }
    }

    private List<String> pdfLines(AuditExportJob job, ExecutionEvidencePack evidence) {
        List<String> lines = new ArrayList<>();
        lines.add("Execution ID: " + evidence.executionId());
        lines.add("Trace ID: " + evidence.traceId());
        lines.add("Audit ID: " + nullable(evidence.audit().auditId()));
        lines.add("Institution: " + evidence.institutionId());
        lines.add("Workload / Pack: " + evidence.workloadId() + " / " + job.executionPack().name());
        lines.add("Purpose: " + evidence.purposeCode());
        lines.add("Runtime / Decision: " + evidence.runtimeStatus() + " / " + nullable(evidence.policy().finalAction()));
        lines.add("Reason codes: " + nullable(evidence.audit().reasonCode()));
        lines.add("Policy version: " + nullable(evidence.policy().policyVersion()));
        lines.add("Policy snapshot digest: " + nullable(evidence.policy().snapshotDigest()));
        lines.add("Destination: " + nullable(evidence.egress().destinationProfileId()) + " / "
            + nullable(evidence.egress().destinationProfileVersion()));
        lines.add("Connector / Response Guard: " + nullable(evidence.egress().connectorStatus()) + " / "
            + nullable(evidence.egress().responseGuardStatus()));
        lines.add("Recovery / External status: " + nullable(evidence.recovery().recoveryStatus()) + " / "
            + nullable(evidence.recovery().lastObservedExternalStatus()));
        lines.add("Source window: " + evidence.createdAt() + " - " + evidence.updatedAt());
        lines.add("Evidence digest: " + evidence.exportContentDigest());
        return lines;
    }

    private float line(PDPageContentStream stream, PDType1Font font, float size, float x, float y, String value)
        throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(ascii(value));
        stream.endText();
        return y - 17;
    }

    private String csvRow(List<String> values) {
        return values.stream().map(this::csvCell).collect(java.util.stream.Collectors.joining(","));
    }

    private String csvCell(String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private String nullable(String value) {
        return value == null ? "" : value;
    }

    private String ascii(String value) {
        return value.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private String safeFilePart(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
