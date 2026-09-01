package com.adp.gateway.egress.application;

import java.util.List;

public class OutboundGuardException extends RuntimeException {

    private final List<String> reasonCodes;

    public OutboundGuardException(String message, List<String> reasonCodes) {
        super(message);
        this.reasonCodes = List.copyOf(reasonCodes);
    }

    public List<String> reasonCodes() {
        return reasonCodes;
    }
}
