package com.adp.gateway.operations.api;

import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.operations.application.ReviewQueueReadService;
import com.adp.gateway.operations.domain.ReviewQueueDetail;
import com.adp.gateway.operations.domain.ReviewQueuePage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/review-queue")
public class ReviewQueueController {
    private final ReviewQueueReadService service;

    public ReviewQueueController(ReviewQueueReadService service) {
        this.service = service;
    }

    @GetMapping
    ReviewQueuePage search(
        @RequestParam(required = false) ExecutionPackType executionPack,
        @RequestParam(required = false) @Size(max = 120) String workloadId,
        @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
        Authentication authentication
    ) {
        return service.search(
            principal(authentication), executionPack, blankToNull(workloadId), page, size
        );
    }

    @GetMapping("/{executionId}")
    ReviewQueueDetail detail(
        @PathVariable @Size(max = 80) String executionId,
        Authentication authentication
    ) {
        return service.load(principal(authentication), executionId);
    }

    private AuthPrincipal principal(Authentication authentication) {
        return (AuthPrincipal) authentication.getPrincipal();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
