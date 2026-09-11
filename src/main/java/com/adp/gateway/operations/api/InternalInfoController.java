package com.adp.gateway.operations.api;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.boot.info.BuildProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal")
public class InternalInfoController {

    private final Clock clock;
    private final BuildProperties buildProperties;
    private final String runtimeProfile;
    private final String dataProvenance;

    public InternalInfoController(
        Clock clock,
        BuildProperties buildProperties,
        @Value("${adp.environment.profile:production-like}") String runtimeProfile,
        @Value("${adp.environment.data-provenance:NONE}") String dataProvenance
    ) {
        this.clock = clock;
        this.buildProperties = buildProperties;
        this.runtimeProfile = runtimeProfile;
        this.dataProvenance = dataProvenance;
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        return ResponseEntity.ok(Map.of(
            "service", buildProperties.getName(),
            "version", buildProperties.getVersion(),
            "runtimeProfile", runtimeProfile,
            "dataProvenance", dataProvenance,
            "timestamp", OffsetDateTime.now(clock).toString()
        ));
    }
}
