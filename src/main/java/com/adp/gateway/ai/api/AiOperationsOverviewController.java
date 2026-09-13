package com.adp.gateway.ai.api;

import java.time.OffsetDateTime;

import com.adp.gateway.ai.application.AiOperationsOverviewService;
import com.adp.gateway.ai.domain.AiOperationsOverview;
import com.adp.gateway.auth.domain.AuthPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai")
public class AiOperationsOverviewController {
    private final AiOperationsOverviewService service;

    public AiOperationsOverviewController(AiOperationsOverviewService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    AiOperationsOverview overview(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime to,
        @RequestParam(required = false) String query,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        Authentication authentication
    ) {
        return service.load((AuthPrincipal) authentication.getPrincipal(), from, to, query, status, page, size);
    }
}
