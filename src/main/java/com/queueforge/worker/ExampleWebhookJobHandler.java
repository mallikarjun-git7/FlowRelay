
package com.queueforge.worker;

import com.queueforge.domain.Job;
import com.queueforge.worker.webhook.WebhookDeliveryService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class ExampleWebhookJobHandler implements JobHandler {

    private final JobHandlerRegistry registry;
    private final WebhookDeliveryService deliveryService;

    public ExampleWebhookJobHandler(
            JobHandlerRegistry registry,
            WebhookDeliveryService deliveryService) {
        this.registry = registry;
        this.deliveryService = deliveryService;
    }

    @PostConstruct
    void init() {
        registry.register(this);
    }

    @Override
    public String type() {
        return "example.webhook";
    }

    @Override
    public void handle(Job job) {
        try {
            deliveryService.deliver(job.payload());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Webhook delivery failed", e);
        }
    }
}
