package com.jayagent.jayagent_review.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class WebhookReviewTaskRepository {

    private static final long DEFAULT_LOCK_TIMEOUT_MS = 5 * 60 * 1000L;
    private final Path databaseFile;

    public WebhookReviewTaskRepository(@Value("${app.webhook-review-task.database-file:data/webhook-review-task.db}") String databaseFile) {
        this.databaseFile = Paths.get(databaseFile);
    }

    @PostConstruct
    void init() {
        try {
            Path parent = databaseFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Connection connection = connect()) {
                createSchema(connection);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize webhook review task repository", ex);
        }
    }

    public synchronized WebhookReviewTask enqueue(String platform, String requestId, String rawBody) throws SQLException {
        WebhookReviewTask task = new WebhookReviewTask(
                UUID.randomUUID().toString(),
                platform,
                requestId,
                rawBody,
                WebhookReviewTaskStatus.PENDING,
                0,
                Instant.now().toEpochMilli(),
                (Long) null,
                null,
                null,
                Instant.now().toEpochMilli()
        );
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO webhook_review_task(
                         id, platform, request_id, raw_body, status, attempt_count,
                         next_attempt_at, locked_at, lock_owner, last_error, updated_at
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
            bindTask(statement, task);
            statement.executeUpdate();
        }
        return task;
    }

    public synchronized List<WebhookReviewTask> claimDue(int batchSize, long nowMillis, long staleAfterMillis) throws SQLException {
        List<WebhookReviewTask> tasks = new ArrayList<>();
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                List<String> candidates = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT id FROM webhook_review_task
                        WHERE (status = 'PENDING' AND next_attempt_at <= ?)
                           OR (status = 'RUNNING' AND locked_at IS NOT NULL AND locked_at <= ?)
                        ORDER BY next_attempt_at ASC, updated_at ASC
                        LIMIT ?
                        """)) {
                    statement.setLong(1, nowMillis);
                    statement.setLong(2, nowMillis - staleAfterMillis);
                    statement.setInt(3, batchSize);
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            candidates.add(rs.getString(1));
                        }
                    }
                }
                for (String id : candidates) {
                    Optional<WebhookReviewTask> task = claimById(connection, id, nowMillis);
                    task.ifPresent(tasks::add);
                }
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                if (ex instanceof SQLException sqlException) {
                    throw sqlException;
                }
                throw new SQLException("Failed to claim webhook review tasks", ex);
            }
        }
        return tasks;
    }

    public synchronized void markSuccess(String id) throws SQLException {
        updateStatus(id, WebhookReviewTaskStatus.SUCCEEDED, null, null, null);
    }

    public synchronized void markRetry(String id, String error, long nextAttemptAt) throws SQLException {
        updateStatus(id, WebhookReviewTaskStatus.PENDING, error, nextAttemptAt, null);
    }

    public synchronized void markFailed(String id, String error) throws SQLException {
        updateStatus(id, WebhookReviewTaskStatus.FAILED, error, null, null);
    }

    private Optional<WebhookReviewTask> claimById(Connection connection, String id, long nowMillis) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE webhook_review_task
                SET status = 'RUNNING', locked_at = ?, updated_at = ?
                WHERE id = ? AND (
                    status = 'PENDING' OR
                    (status = 'RUNNING' AND locked_at IS NOT NULL AND locked_at <= ?)
                )
                """)) {
            statement.setLong(1, nowMillis);
            statement.setLong(2, nowMillis);
            statement.setString(3, id);
            statement.setLong(4, nowMillis - DEFAULT_LOCK_TIMEOUT_MS);
            if (statement.executeUpdate() == 0) {
                return Optional.empty();
            }
        }
        return findById(connection, id);
    }

    public synchronized Optional<WebhookReviewTask> findById(String id) throws SQLException {
        try (Connection connection = connect()) {
            return findById(connection, id);
        }
    }

    private Optional<WebhookReviewTask> findById(Connection connection, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM webhook_review_task WHERE id = ?")) {
            statement.setString(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private void updateStatus(String id, WebhookReviewTaskStatus status, String error, Long nextAttemptAt, Long lockedAt) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE webhook_review_task
                     SET status = ?, last_error = ?, next_attempt_at = COALESCE(?, next_attempt_at), locked_at = ?, updated_at = ?
                     WHERE id = ?
                     """)) {
            statement.setString(1, status.name());
            statement.setString(2, error);
            if (nextAttemptAt == null) {
                statement.setNull(3, java.sql.Types.BIGINT);
            } else {
                statement.setLong(3, nextAttemptAt);
            }
            if (lockedAt == null) {
                statement.setNull(4, java.sql.Types.BIGINT);
            } else {
                statement.setLong(4, lockedAt);
            }
            statement.setLong(5, Instant.now().toEpochMilli());
            statement.setString(6, id);
            statement.executeUpdate();
        }
    }

    private void createSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS webhook_review_task(
                        id TEXT PRIMARY KEY,
                        platform TEXT NOT NULL,
                        request_id TEXT,
                        raw_body TEXT NOT NULL,
                        status TEXT NOT NULL,
                        attempt_count INTEGER NOT NULL,
                        next_attempt_at INTEGER NOT NULL,
                        locked_at INTEGER,
                        lock_owner TEXT,
                        last_error TEXT,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_webhook_review_task_status_next ON webhook_review_task(status, next_attempt_at)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_webhook_review_task_locked_at ON webhook_review_task(locked_at)");
        }
    }

    private void bindTask(PreparedStatement statement, WebhookReviewTask task) throws SQLException {
        statement.setString(1, task.id());
        statement.setString(2, task.platform());
        statement.setString(3, task.requestId());
        statement.setString(4, task.rawBody());
        statement.setString(5, task.status().name());
        statement.setInt(6, task.attemptCount());
        statement.setLong(7, task.nextAttemptAt());
        if (task.lockedAt() == null) {
            statement.setNull(8, java.sql.Types.BIGINT);
        } else {
            statement.setLong(8, task.lockedAt());
        }
        statement.setString(9, task.lockOwner());
        statement.setString(10, task.lastError());
        statement.setLong(11, task.updatedAt());
    }

    private WebhookReviewTask map(ResultSet rs) throws SQLException {
        return new WebhookReviewTask(
                rs.getString("id"),
                rs.getString("platform"),
                rs.getString("request_id"),
                rs.getString("raw_body"),
                WebhookReviewTaskStatus.valueOf(rs.getString("status")),
                rs.getInt("attempt_count"),
                rs.getLong("next_attempt_at"),
                rs.getObject("locked_at") == null ? null : rs.getLong("locked_at"),
                rs.getString("lock_owner"),
                rs.getString("last_error"),
                rs.getLong("updated_at")
        );
    }

    Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath());
    }

    public enum WebhookReviewTaskStatus {
        PENDING, RUNNING, SUCCEEDED, FAILED
    }

    public record WebhookReviewTask(
            String id,
            String platform,
            String requestId,
            String rawBody,
            WebhookReviewTaskStatus status,
            int attemptCount,
            long nextAttemptAt,
            Long lockedAt,
            String lockOwner,
            String lastError,
            long updatedAt) {
    }
}
