package com.adp.gateway.evidence.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "adp.regulatory-refresh.enabled", havingValue = "true")
public class RegulatoryRefreshSchedulerConfiguration {
}
