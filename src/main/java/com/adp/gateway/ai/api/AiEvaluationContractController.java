package com.adp.gateway.ai.api;

import com.adp.gateway.ai.application.AiEvaluationContractService;
import com.adp.gateway.ai.domain.AiEvaluationContractSnapshot;
import com.adp.gateway.auth.domain.AuthPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/ai/evaluation-runs")
public class AiEvaluationContractController {
    private final AiEvaluationContractService contracts;
    public AiEvaluationContractController(AiEvaluationContractService contracts) { this.contracts = contracts; }
    @PostMapping("/{runId}/contract/freeze")
    AiEvaluationContractSnapshot freeze(@PathVariable String runId, Authentication authentication) {
        return contracts.freeze((AuthPrincipal) authentication.getPrincipal(), runId);
    }
    @GetMapping("/{runId}/contract")
    AiEvaluationContractSnapshot read(@PathVariable String runId, Authentication authentication) {
        return contracts.read((AuthPrincipal) authentication.getPrincipal(), runId);
    }
}
