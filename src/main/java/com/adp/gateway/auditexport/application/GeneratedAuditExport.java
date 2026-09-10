package com.adp.gateway.auditexport.application;

public record GeneratedAuditExport(
    byte[] content,
    String contentDigest,
    String contentType,
    String fileName,
    int rowCount
) {
    public GeneratedAuditExport {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
