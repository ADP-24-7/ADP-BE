package com.adp.gateway.operations.api;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal")
public class InternalInfoController {

    private final Clock clock;
    private final BuildProperties buildProperties;

    public InternalInfoController(Clock clock, BuildProperties buildProperties) {
        this.clock = clock;
        this.buildProperties = buildProperties;
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        return ResponseEntity.ok(Map.of(
            "service", buildProperties.getName(),
            "version", buildProperties.getVersion(),
            "timestamp", OffsetDateTime.now(clock).toString()
        ));
    }
}
