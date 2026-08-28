package com.jayagent.jayagent_review.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class ObservabilityMetrics {
    private final MeterRegistry registry;

    public ObservabilityMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void webhookReceived(String platform) {
        counter("jayagent_webhook_received_total", "platform", platform).increment();
    }

    public void webhookRejected(String platform, String reason) {
        counter("jayagent_webhook_rejected_total", "platform", platform, "reason", reason).increment();
    }

    public void webhookDuplicate(String platform) {
        counter("jayagent_webhook_duplicate_total", "platform", platform).increment();
    }

    public void reviewCompleted(String source, boolean success) {
        counter("jayagent_review_total", "source", source, "result", success ? "success" : "failure").increment();
    }

    public void llmCall(long elapsedNanos, boolean success) {
        timer("jayagent_llm_call_duration", "result", success ? "success" : "failure")
                .record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    public void ragSearch(long elapsedNanos, boolean success) {
        timer("jayagent_rag_search_duration", "result", success ? "success" : "failure")
                .record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    public void externalApi(String system, String operation, boolean success, long elapsedNanos) {
        counter("jayagent_external_api_total", "system", system, "operation", operation,
                "result", success ? "success" : "failure").increment();
        timer("jayagent_external_api_duration", "system", system, "operation", operation)
                .record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    public void notification(boolean success) {
        counter("jayagent_wechat_notification_total", "result", success ? "success" : "failure").increment();
    }

    private Counter counter(String name, String... tags) {
        return registry.counter(name, tags);
    }

    private Timer timer(String name, String... tags) {
        return registry.timer(name, tags);
    }
}
