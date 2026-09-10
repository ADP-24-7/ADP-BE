package com.adp.gateway.auditexport.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "adp.audit-export.scheduler.enabled", havingValue = "true")
public class AuditExportSchedulerConfiguration {
}
