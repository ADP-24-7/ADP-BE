package com.adp.gateway.evidence.infrastructure;

import com.adp.gateway.evidence.application.RegulatoryRefreshService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.regulatory-refresh.enabled", havingValue = "true")
public class RegulatoryRefreshScheduler {
    private static final Logger log = LoggerFactory.getLogger(RegulatoryRefreshScheduler.class);
    private final RegulatoryRefreshService service;

    public RegulatoryRefreshScheduler(RegulatoryRefreshService service) {
        this.service = service;
    }

    @Scheduled(cron = "${adp.regulatory-refresh.cron:0 0 3 * * *}")
    public void refresh() {
        try {
            service.refresh(null);
        } catch (RuntimeException exception) {
            log.atWarn()
                .addKeyValue("event", "regulatory_refresh_failure")
                .addKeyValue("outcome", "FAILED")
                .log("Scheduled regulatory refresh failed; active policy remains unchanged");
        }
    }
}
