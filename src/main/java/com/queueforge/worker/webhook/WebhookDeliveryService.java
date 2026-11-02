
package com.queueforge.worker.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

@Service
public class WebhookDeliveryService {

    private static final Set<String> ALLOWED_HOSTS =
            Set.of("webhook.site");

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public void deliver(JsonNode payload) throws Exception {

        if (payload == null || !payload.hasNonNull("url")) {
            throw new IllegalArgumentException("Webhook URL required");
        }

        URI uri = URI.create(payload.get("url").asText());

        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || !ALLOWED_HOSTS.contains(uri.getHost().toLowerCase())
                || uri.getPort() != -1
                || uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException(
                    "Webhook destination is not allowed");
        }

        String body = payload.has("body")
                ? payload.get("body").toString()
                : "{}";

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Webhook returned HTTP " + response.statusCode());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Webhook delivery interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Webhook delivery failed", e);
        }
    }
}
