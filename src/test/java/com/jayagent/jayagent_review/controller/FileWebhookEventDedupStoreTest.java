package com.jayagent.jayagent_review.controller;

import com.jayagent.jayagent_review.config.JayAgentProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileWebhookEventDedupStoreTest {

    @Test
    void dedupEntriesExpireAfterTtl() throws Exception {
        Path tempFile = Files.createTempFile("webhook-dedup", ".log");
        Clock baseClock = Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC);
        FileWebhookEventDedupStore store = new FileWebhookEventDedupStore(tempFile.toString(), Duration.ofDays(1), baseClock);
        store.init();

        assertTrue(store.markProcessed("github:delivery-1"));
        assertFalse(store.markProcessed("github:delivery-1"));

        Clock laterClock = Clock.fixed(Instant.parse("2026-08-21T00:00:00Z"), ZoneOffset.UTC);
        FileWebhookEventDedupStore reloaded = new FileWebhookEventDedupStore(tempFile.toString(), Duration.ofDays(1), laterClock);
        reloaded.init();

        assertTrue(reloaded.markProcessed("github:delivery-1"));
    }

    @Test
    void configuredPathIsUsedByProperties() throws Exception {
        Path tempFile = Files.createTempFile("configured-webhook-dedup", ".log");
        JayAgentProperties properties = new JayAgentProperties();
        properties.getWebhook().setDedupStore(tempFile.toString());

        FileWebhookEventDedupStore store = new FileWebhookEventDedupStore(properties);
        store.init();

        assertTrue(store.markProcessed("configured-key"));
        assertFalse(Files.readString(tempFile).isBlank());
    }
}
