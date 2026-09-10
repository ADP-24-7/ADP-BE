package com.adp.gateway.auditexport.domain;

public record AuditExportDownload(byte[] content, String contentType, String fileName, String contentDigest) {
    public AuditExportDownload {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
