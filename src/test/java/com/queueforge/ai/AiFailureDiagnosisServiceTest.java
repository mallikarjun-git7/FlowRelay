package com.queueforge.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queueforge.application.GetJobUseCase;
import com.queueforge.application.JobEventService;
import com.queueforge.domain.Job;
import com.queueforge.domain.JobEvent;
import com.queueforge.domain.JobPriority;
import com.queueforge.domain.JobStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiFailureDiagnosisServiceTest {
    @Mock GetJobUseCase jobs;
    @Mock JobEventService events;
    @Mock OpenAiResponsesGateway gateway;

    private Job job(UUID id, JobStatus status) throws Exception {
        return new Job(id, "default", "example.webhook",
            new ObjectMapper().readTree("{\"private\":\"NEVER_SEND_THIS_PAYLOAD\"}"),
            status, JobPriority.NORMAL, Instant.now(), 2, 2,
            null, null, null,
            "HTTP 429 from https://example.org/x?token=private password=supersecret test@example.com",
            Instant.now(), Instant.now(), null, null);
    }

    @Test
    void diagnosisUsesLimitedRedactedMetadataAndReturnsAdvice() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobs.getById(id)).thenReturn(job(id, JobStatus.DEAD_LETTERED));
        when(events.getTimeline(id)).thenReturn(List.of(
            new JobEvent(1L, id, "JOB_FAILED", "RUNNING", "RETRY_SCHEDULED",
                "worker-secret", "NEVER_SEND_EVENT_MESSAGE", null, Instant.now())));
        when(gateway.analyze(anyString()))
            .thenReturn(new AiDiagnosisGateway.Analysis("gpt-6-luna", "Possible rate limit; inspect 429 responses."));
        AiDiagnosisResponse result = new AiFailureDiagnosisService(jobs, events, gateway).diagnose(id);
        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(gateway).analyze(sent.capture());

        assertThat(result.jobStatus()).isEqualTo("DEAD_LETTERED");
        assertThat(result.analysis()).contains("rate limit");
        assertThat(sent.getValue()).contains("HTTP 429", "JOB_FAILED");
        assertThat(sent.getValue()).doesNotContain(
            "NEVER_SEND_THIS_PAYLOAD", "NEVER_SEND_EVENT_MESSAGE", "worker-secret",
            "supersecret", "test@example.com", "example.org");
    }

    @Test
    void completedJobCannotBeDiagnosed() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobs.getById(id)).thenReturn(job(id, JobStatus.COMPLETED));
        assertThatThrownBy(() -> new AiFailureDiagnosisService(jobs, events, gateway).diagnose(id))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");
        verifyNoInteractions(events, gateway);
    }

    @Test
    void redactionRemovesBearerTokensAndCapsErrorLength() {
        String text = AiFailureDiagnosisService.scrub("Bearer tokenXYZ " + "x".repeat(900), 60);
        assertThat(text).doesNotContain("tokenXYZ");
        assertThat(text.length()).isLessThanOrEqualTo(63);
    }
}
