package com.jayagent.jayagent_review.integration;

import org.slf4j.Logger;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import com.jayagent.jayagent_review.observability.ObservabilityMetrics;

public class ExternalApiCallSupport {
    private final ObservabilityMetrics metrics;

    public ExternalApiCallSupport() {
        this(null);
    }

    public ExternalApiCallSupport(ObservabilityMetrics metrics) {
        this.metrics = metrics;
    }

    public SimpleClientHttpRequestFactory createRequestFactory(int connectTimeoutMillis, int readTimeoutMillis) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMillis);
        factory.setReadTimeout(readTimeoutMillis);
        return factory;
    }

    public <T> T executeWithRetry(String system,
                                  String operation,
                                  String target,
                                  int maxRetries,
                                  long retryBackoffMillis,
                                  RetryAction<T> action,
                                  Logger log) {
        long startedAt = System.nanoTime();
        RestClientException last = null;
        int attempts = Math.max(0, maxRetries) + 1;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                T result = action.execute();
                log.info("external_api_success system={} operation={} target={} attempt={} costMs={}",
                        system, operation, target, attempt + 1, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
                if (metrics != null) {
                    metrics.externalApi(system, operation, true, System.nanoTime() - startedAt);
                }
                return result;
            } catch (RestClientException ex) {
                last = ex;
                if (attempt >= attempts - 1) {
                    break;
                }
                sleepBackoff(retryBackoffMillis, attempt);
            }
        }

        log.warn("external_api_failure system={} operation={} target={} attempts={} costMs={}",
                system, operation, target, attempts, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt), last);
        if (metrics != null) {
            metrics.externalApi(system, operation, false, System.nanoTime() - startedAt);
        }
        throw new ExternalApiException(system, operation, "Failed to call external API after retries.", last);
    }

    private void sleepBackoff(long retryBackoffMillis, int attempt) {
        if (retryBackoffMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(retryBackoffMillis * (attempt + 1L));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    public interface RetryAction<T> {
        T execute();
    }
}
