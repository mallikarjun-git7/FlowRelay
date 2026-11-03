package com.queueforge.ai;

import java.util.UUID;

public record AiDiagnosisResponse(
    UUID jobId,
    String jobStatus,
    String model,
    String analysis,
    String disclaimer
) {}
