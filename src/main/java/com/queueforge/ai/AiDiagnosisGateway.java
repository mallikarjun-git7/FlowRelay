package com.queueforge.ai;

/** Provider-neutral, read-only failure analysis interface. */
public interface AiDiagnosisGateway {
    Analysis analyze(String context);
    record Analysis(String model, String text) {}
}
