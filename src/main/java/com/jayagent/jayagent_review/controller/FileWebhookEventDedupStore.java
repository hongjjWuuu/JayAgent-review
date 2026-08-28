package com.jayagent.jayagent_review.controller;

import com.jayagent.jayagent_review.config.JayAgentProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.jayagent.webhook.dedup-store-type", havingValue = "file", matchIfMissing = true)
public class FileWebhookEventDedupStore implements WebhookEventDedupStore {

    private static final Logger log = LoggerFactory.getLogger(FileWebhookEventDedupStore.class);

    private final Map<String, Long> cache = new LinkedHashMap<>();
    private final Path storePath;
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public FileWebhookEventDedupStore(JayAgentProperties properties) {
        this(properties.getWebhook().getDedupStore(),
                Duration.ofDays(Math.max(1L, properties.getWebhook().getDedup().getTtlDays())), Clock.systemUTC());
    }

    FileWebhookEventDedupStore(String storeLocation, Duration ttl, Clock clock) {
        this.storePath = Paths.get(storeLocation);
        this.ttl = ttl;
        this.clock = clock;
    }

    @PostConstruct
    void init() {
        try {
            Path parent = storePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.exists(storePath)) {
                List<String> lines = Files.readAllLines(storePath, StandardCharsets.UTF_8);
                for (String line : lines) {
                    ParsedEntry entry = parse(line);
                    if (entry != null && !isExpired(entry.expiresAtMillis())) {
                        cache.put(entry.key(), entry.expiresAtMillis());
                    }
                }
                rewriteStore();
            }
        } catch (IOException ex) {
            log.warn("Failed to initialize webhook dedup store, falling back to in-memory only.", ex);
        }
    }

    @Override
    public synchronized boolean markProcessed(String dedupeKey) {
        purgeExpired();
        if (cache.containsKey(dedupeKey)) {
            return false;
        }

        long expiresAt = clock.millis() + ttl.toMillis();
        cache.put(dedupeKey, expiresAt);
        rewriteStore();
        return true;
    }

    private void purgeExpired() {
        boolean changed = cache.entrySet().removeIf(entry -> isExpired(entry.getValue()));
        if (changed) {
            rewriteStore();
        }
    }

    private boolean isExpired(long expiresAtMillis) {
        return clock.millis() >= expiresAtMillis;
    }

    private void rewriteStore() {
        try {
            Path parent = storePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            StringBuilder content = new StringBuilder();
            for (Map.Entry<String, Long> entry : cache.entrySet()) {
                content.append(entry.getKey())
                        .append('|')
                        .append(entry.getValue())
                        .append(System.lineSeparator());
            }
            Files.writeString(storePath, content.toString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.warn("Failed to persist webhook dedupe store: {}", storePath, ex);
        }
    }

    private ParsedEntry parse(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        int separator = line.lastIndexOf('|');
        if (separator < 0) {
            return new ParsedEntry(line.trim(), clock.millis() + ttl.toMillis());
        }
        String key = line.substring(0, separator).trim();
        String expiresAtText = line.substring(separator + 1).trim();
        try {
            long expiresAt = Long.parseLong(expiresAtText);
            return new ParsedEntry(key, expiresAt);
        } catch (NumberFormatException ex) {
            return new ParsedEntry(line.trim(), clock.millis() + ttl.toMillis());
        }
    }

    private record ParsedEntry(String key, long expiresAtMillis) {
    }
}
