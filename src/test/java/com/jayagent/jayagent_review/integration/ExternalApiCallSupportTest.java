package com.jayagent.jayagent_review.integration;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.web.client.RestClientException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ExternalApiCallSupportTest {

    private final ExternalApiCallSupport support = new ExternalApiCallSupport();

    @Test
    void executeWithRetryEventuallySucceeds() {
        AtomicInteger attempts = new AtomicInteger();

        String value = support.executeWithRetry("GitHub", "getPrFiles", "owner/repo#1",
                2, 0, () -> {
                    if (attempts.incrementAndGet() < 3) {
                        throw new RestClientException("temporary");
                    }
                    return "ok";
                }, mock(Logger.class));

        assertEquals("ok", value);
        assertEquals(3, attempts.get());
    }

    @Test
    void executeWithRetryFailsAfterRetries() {
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(ExternalApiException.class, () ->
                support.executeWithRetry("GitLab", "postComment", "project#1",
                        1, 0, () -> {
                            attempts.incrementAndGet();
                            throw new RestClientException("boom");
                        }, mock(Logger.class)));

        assertEquals(2, attempts.get());
    }
}
