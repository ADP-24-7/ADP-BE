package com.adp.gateway.auditexport.application;

public class AuditExportException extends RuntimeException {
    private final String reasonCode;

    public AuditExportException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
