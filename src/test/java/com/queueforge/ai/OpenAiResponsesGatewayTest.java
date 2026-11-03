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

class OpenAiResponsesGatewayTest {
    @Test
    void missingKeyFailsWithoutNetworkAccess() {
        HttpClient client = mock(HttpClient.class);
        var gateway = new OpenAiResponsesGateway(new ObjectMapper(), "", "gpt-6-luna", client);
        assertThatThrownBy(() -> gateway.analyze("failure"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("503");
        verifyNoInteractions(client);
    }

    @Test
    @SuppressWarnings("unchecked")
    void extractsOutputTextFromCompletedResponse() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
          {"status":"completed","output":[{"type":"message","content":[
            {"type":"output_text","text":"Inspect the retry policy."}]}]}
          """);
        when(client.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
            .thenReturn(response);
        var gateway = new OpenAiResponsesGateway(new ObjectMapper(), "test-key", "gpt-6-luna", client);
        var result = gateway.analyze("HTTP 429");
        assertThat(result.text()).isEqualTo("Inspect the retry policy.");
        assertThat(result.model()).isEqualTo("gpt-6-luna");
    }

    @Test
    @SuppressWarnings("unchecked")
    void providerErrorsDoNotLeakResponseBody() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(401);
        when(client.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
            .thenReturn(response);
        var gateway = new OpenAiResponsesGateway(new ObjectMapper(), "test-key", "gpt-6-luna", client);
        assertThatThrownBy(() -> gateway.analyze("HTTP 429"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("502");
    }
}
