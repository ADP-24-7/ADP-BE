package com.adp.gateway.evidence.api;

import com.adp.gateway.evidence.application.RegulatoryRefreshService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/regulatory-refresh")
public class RegulatoryRefreshController {
    private final RegulatoryRefreshService service;

    public RegulatoryRefreshController(RegulatoryRefreshService service) {
        this.service = service;
    }

    @PostMapping
    JsonNode refresh(@Valid @RequestBody RegulatoryRefreshRequest request) {
        return service.refresh(request.sourceIds());
    }
}
