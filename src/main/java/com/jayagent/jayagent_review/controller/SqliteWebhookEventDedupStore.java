package com.jayagent.jayagent_review.controller;

import com.jayagent.jayagent_review.config.JayAgentProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

/**
 * SQLite-backed idempotency store. The database unique key makes concurrent
 * claims atomic across multiple JVMs sharing the same database file.
 */
@Component
@ConditionalOnProperty(name = "app.jayagent.webhook.dedup-store-type", havingValue = "sqlite")
public class SqliteWebhookEventDedupStore implements WebhookEventDedupStore {

    private static final Logger log = LoggerFactory.getLogger(SqliteWebhookEventDedupStore.class);

    private final String databaseFile;
    private final long ttlMillis;

    public SqliteWebhookEventDedupStore(JayAgentProperties properties) {
        this.databaseFile = properties.getWebhook().getDedupStore();
        this.ttlMillis = Duration.ofDays(Math.max(1L, properties.getWebhook().getDedup().getTtlDays())).toMillis();
    }

    @PostConstruct
    void init() {
        try (Connection connection = open()) {
            createSchema(connection);
            purgeExpired(connection, System.currentTimeMillis());
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to initialize SQLite webhook dedup store: " + databaseFile, ex);
        }
    }

    @Override
    public boolean markProcessed(String dedupeKey) {
        long now = System.currentTimeMillis();
        try (Connection connection = open()) {
            createSchema(connection);
            purgeExpired(connection, now);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT OR IGNORE INTO webhook_dedup(event_key, expires_at) VALUES (?, ?)")) {
                statement.setString(1, dedupeKey);
                statement.setLong(2, now + ttlMillis);
                return statement.executeUpdate() == 1;
            }
        } catch (SQLException ex) {
            log.warn("Failed to persist webhook deduplication key: {}", dedupeKey, ex);
            return false;
        }
    }

    private Connection open() throws SQLException {
        Path path = Paths.get(databaseFile);
        Path parent = path.toAbsolutePath().getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception ex) {
            throw new SQLException("Failed to create dedup database directory: " + parent, ex);
        }
        return DriverManager.getConnection("jdbc:sqlite:" + path);
    }

    private void createSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS webhook_dedup ("
                    + "event_key TEXT PRIMARY KEY, expires_at INTEGER NOT NULL)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_webhook_dedup_expires_at "
                    + "ON webhook_dedup(expires_at)");
        }
    }

    private void purgeExpired(Connection connection, long now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM webhook_dedup WHERE expires_at <= ?")) {
            statement.setLong(1, now);
            statement.executeUpdate();
        }
    }
}
