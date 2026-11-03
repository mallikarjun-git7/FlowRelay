package com.queueforge.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OllamaDiagnosisGatewayTest {
    @Test
    @SuppressWarnings("unchecked")
    void parsesLocalAnswerAndUsesConfiguredModel() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"response\":\"Likely cause: simulated failure\",\"done\":true}");
        when(client.send(any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);
        var gateway = new OllamaDiagnosisGateway(new ObjectMapper(),
            "http://localhost:11434/api/generate", "qwen2.5:1.5b", client);
        var result = gateway.analyze("Two attempts failed");
        assertThat(result.model()).isEqualTo("qwen2.5:1.5b");
        assertThat(result.text()).contains("simulated failure");
    }

    @Test
    @SuppressWarnings("unchecked")
    void providerErrorsAreSafeAndBecomeBadGateway() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(404);
        when(client.send(any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);
        var gateway = new OllamaDiagnosisGateway(new ObjectMapper(),
            "http://localhost:11434/api/generate", "missing-model", client);
        assertThatThrownBy(() -> gateway.analyze("test"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("502");
    }
}
