package com.adp.gateway.digitalasset.api;

import java.time.OffsetDateTime;

import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.digitalasset.application.DigitalAssetOperationsOverviewService;
import com.adp.gateway.digitalasset.domain.DigitalAssetOperationsOverview;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/digital-assets")
public class DigitalAssetOperationsOverviewController {
    private final DigitalAssetOperationsOverviewService service;

    public DigitalAssetOperationsOverviewController(DigitalAssetOperationsOverviewService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    DigitalAssetOperationsOverview overview(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime to,
        Authentication authentication
    ) {
        return service.load((AuthPrincipal) authentication.getPrincipal(), from, to);
    }
}
