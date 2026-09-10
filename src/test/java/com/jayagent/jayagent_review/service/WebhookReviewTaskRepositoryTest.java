package com.jayagent.jayagent_review.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookReviewTaskRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void incrementsAttemptCountWhenTaskIsClaimedAndPersistsAcrossRetries() throws Exception {
        WebhookReviewTaskRepository repository =
                new WebhookReviewTaskRepository(tempDir.resolve("tasks.db").toString());
        repository.init();

        WebhookReviewTaskRepository.WebhookReviewTask enqueued =
                repository.enqueue("github", "request-1", "{}");
        assertEquals(0, enqueued.attemptCount());

        long baseTime = enqueued.nextAttemptAt();
        WebhookReviewTaskRepository.WebhookReviewTask firstAttempt =
                claimSingle(repository, baseTime);
        assertEquals(1, firstAttempt.attemptCount());

        repository.markRetry(firstAttempt.id(), "first failure", baseTime + 1_000L);
        WebhookReviewTaskRepository.WebhookReviewTask secondAttempt =
                claimSingle(repository, baseTime + 1_000L);
        assertEquals(2, secondAttempt.attemptCount());

        repository.markRetry(secondAttempt.id(), "second failure", baseTime + 2_000L);
        WebhookReviewTaskRepository.WebhookReviewTask thirdAttempt =
                claimSingle(repository, baseTime + 2_000L);
        assertEquals(3, thirdAttempt.attemptCount());

        repository.markFailed(thirdAttempt.id(), "final failure");
        WebhookReviewTaskRepository.WebhookReviewTask failed =
                repository.findById(thirdAttempt.id()).orElseThrow();
        assertEquals(WebhookReviewTaskRepository.WebhookReviewTaskStatus.FAILED, failed.status());
        assertEquals(3, failed.attemptCount());
    }

    @Test
    void restoresPendingRetryAfterRepositoryIsRecreated() throws Exception {
        Path databaseFile = tempDir.resolve("restart.db");
        WebhookReviewTaskRepository firstRepository =
                new WebhookReviewTaskRepository(databaseFile.toString());
        firstRepository.init();

        WebhookReviewTaskRepository.WebhookReviewTask enqueued =
                firstRepository.enqueue("gitlab", "request-restart", "{}");
        WebhookReviewTaskRepository.WebhookReviewTask firstAttempt =
                claimSingle(firstRepository, enqueued.nextAttemptAt());
        firstRepository.markRetry(firstAttempt.id(), "temporary failure", enqueued.nextAttemptAt() + 1_000L);

        WebhookReviewTaskRepository restartedRepository =
                new WebhookReviewTaskRepository(databaseFile.toString());
        restartedRepository.init();

        WebhookReviewTaskRepository.WebhookReviewTask restored =
                restartedRepository.findById(firstAttempt.id()).orElseThrow();
        assertEquals(WebhookReviewTaskRepository.WebhookReviewTaskStatus.PENDING, restored.status());
        assertEquals(1, restored.attemptCount());
        assertEquals("temporary failure", restored.lastError());

        WebhookReviewTaskRepository.WebhookReviewTask secondAttempt =
                claimSingle(restartedRepository, restored.nextAttemptAt());
        assertEquals(2, secondAttempt.attemptCount());
    }

    @Test
    void reclaimsStaleRunningTaskAndIncrementsAttemptCount() throws Exception {
        WebhookReviewTaskRepository repository =
                new WebhookReviewTaskRepository(tempDir.resolve("stale.db").toString());
        repository.init();

        WebhookReviewTaskRepository.WebhookReviewTask enqueued =
                repository.enqueue("github", "request-stale", "{}");
        WebhookReviewTaskRepository.WebhookReviewTask firstAttempt =
                claimSingle(repository, enqueued.nextAttemptAt());

        long staleNow = firstAttempt.lockedAt() + 5 * 60 * 1000L;
        WebhookReviewTaskRepository.WebhookReviewTask recovered =
                claimSingle(repository, staleNow);

        assertEquals(firstAttempt.id(), recovered.id());
        assertEquals(2, recovered.attemptCount());
    }

    private WebhookReviewTaskRepository.WebhookReviewTask claimSingle(
            WebhookReviewTaskRepository repository, long nowMillis) throws Exception {
        List<WebhookReviewTaskRepository.WebhookReviewTask> claimed =
                repository.claimDue(1, nowMillis, 5 * 60 * 1000L);
        assertEquals(1, claimed.size());
        WebhookReviewTaskRepository.WebhookReviewTask task = claimed.get(0);
        assertTrue(task.lockedAt() != null);
        return task;
    }
}
