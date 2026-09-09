package com.adp.gateway.recovery.infrastructure;

import java.util.UUID;

import com.adp.gateway.recovery.application.ExternalInteractionRecoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.recovery.scheduler.enabled", havingValue = "true")
public class ExternalInteractionRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExternalInteractionRecoveryScheduler.class);

    private final ExternalInteractionRecoveryService service;
    private final int batchSize;
    private final String workerId = "scheduler:" + UUID.randomUUID();

    public ExternalInteractionRecoveryScheduler(
        ExternalInteractionRecoveryService service,
        @Value("${adp.recovery.scheduler.batch-size:20}") int batchSize
    ) {
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("Recovery scheduler batch size must be between 1 and 100");
        }
        this.service = service;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${adp.recovery.scheduler.fixed-delay:30s}")
    public void processDueIncidents() {
        int processed = 0;
        try {
            while (processed < batchSize && service.processNext(workerId)) {
                processed++;
            }
        } catch (RuntimeException exception) {
            log.atWarn()
                .addKeyValue("event", "recovery_scheduler_failure")
                .addKeyValue("outcome", "FAILED")
                .log("Recovery scheduler batch stopped after an incident processing failure");
        }
        if (processed > 0) {
            log.atInfo()
                .addKeyValue("event", "recovery_scheduler_batch")
                .addKeyValue("outcome", "PROCESSED")
                .addKeyValue("count", processed)
                .log("Recovery scheduler batch completed");
        }
    }
}
