package com.adp.gateway.recovery.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "adp.recovery.scheduler.enabled", havingValue = "true")
public class RecoverySchedulerConfiguration {
}
