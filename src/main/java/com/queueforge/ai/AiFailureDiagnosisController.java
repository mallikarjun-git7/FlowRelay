package com.queueforge.ai;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/ai")
@Tag(name = "AI Diagnosis", description = "Read-only, on-demand failure analysis")
public class AiFailureDiagnosisController {
    private final AiFailureDiagnosisService service;

    public AiFailureDiagnosisController(AiFailureDiagnosisService service) {
        this.service = service;
    }

    @PostMapping("/jobs/{jobId}/diagnose")
    @Operation(summary = "Analyze a retry-scheduled or dead-lettered job with an LLM")
    public AiDiagnosisResponse diagnose(@PathVariable UUID jobId) {
        return service.diagnose(jobId);
    }
}
