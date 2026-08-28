package com.jayagent.jayagent_review.controller;

import com.jayagent.jayagent_review.config.JayAgentProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteWebhookEventDedupStoreTest {

    @Test
    void onlyOneConcurrentClaimSucceeds() throws Exception {
        Path database = Files.createTempFile("webhook-dedup", ".db");
        JayAgentProperties properties = new JayAgentProperties();
        properties.getWebhook().setDedupStore(database.toString());
        SqliteWebhookEventDedupStore first = new SqliteWebhookEventDedupStore(properties);
        SqliteWebhookEventDedupStore second = new SqliteWebhookEventDedupStore(properties);
        first.init();
        second.init();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        try {
            executor.submit(() -> claim(first, start, successes));
            executor.submit(() -> claim(second, start, successes));
            start.countDown();
        } finally {
            executor.shutdown();
        }

        while (!executor.isTerminated()) {
            Thread.yield();
        }
        assertEquals(1, successes.get());
        assertTrue(Files.size(database) > 0);
    }

    private void claim(SqliteWebhookEventDedupStore store, CountDownLatch start, AtomicInteger successes) {
        try {
            start.await();
            if (store.markProcessed("github:concurrent-event")) {
                successes.incrementAndGet();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
