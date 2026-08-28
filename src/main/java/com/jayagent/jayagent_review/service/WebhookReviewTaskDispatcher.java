package com.jayagent.jayagent_review.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayagent.jayagent_review.agent.JayAgentReport;
import com.jayagent.jayagent_review.integration.ExternalApiException;
import com.jayagent.jayagent_review.integration.GitHubApiClient;
import com.jayagent.jayagent_review.integration.GitLabApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class WebhookReviewTaskDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookReviewTaskDispatcher.class);
    private static final long RETRY_DELAY_MS = 30_000L;
    private final WebhookReviewTaskRepository repository;
    private final WebhookService webhookService;
    private final GitLabApiClient gitLabApiClient;
    private final GitHubApiClient gitHubApiClient;
    private final GitHubWebhookParser githubParser;
    private final GitLabWebhookParser gitlabParser;
    private final DiffAssembler diffAssembler;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    public WebhookReviewTaskDispatcher(WebhookReviewTaskRepository repository, WebhookService webhookService,
                                       GitLabApiClient gitLabApiClient, GitHubApiClient gitHubApiClient,
                                       GitHubWebhookParser githubParser, GitLabWebhookParser gitlabParser,
                                       DiffAssembler diffAssembler, ObjectMapper objectMapper,
                                       @Qualifier("applicationTaskExecutor") Executor executor) {
        this.repository = repository;
        this.webhookService = webhookService;
        this.gitLabApiClient = gitLabApiClient;
        this.gitHubApiClient = gitHubApiClient;
        this.githubParser = githubParser;
        this.gitlabParser = gitlabParser;
        this.diffAssembler = diffAssembler;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    public WebhookReviewTaskRepository.WebhookReviewTask enqueueGitHub(String rawBody, String requestId) {
        return enqueue("github", requestId, rawBody);
    }

    public WebhookReviewTaskRepository.WebhookReviewTask enqueueGitLab(String rawBody, String requestId) {
        return enqueue("gitlab", requestId, rawBody);
    }

    private WebhookReviewTaskRepository.WebhookReviewTask enqueue(String platform, String requestId, String rawBody) {
        try {
            WebhookReviewTaskRepository.WebhookReviewTask task = repository.enqueue(platform, requestId, rawBody);
            log.info("webhook_task_enqueued id={} platform={} requestId={}", task.id(), task.platform(), task.requestId());
            dispatchPending();
            return task;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to enqueue webhook review task", ex);
        }
    }

    @Scheduled(fixedDelayString = "${app.webhook-review-task.poll-interval-ms:5000}")
    public void dispatchPending() {
        try {
            for (WebhookReviewTaskRepository.WebhookReviewTask task : repository.claimDue(10, System.currentTimeMillis(), 5 * 60 * 1000L)) {
                log.info("webhook_task_dispatching id={} platform={} requestId={}", task.id(), task.platform(), task.requestId());
                CompletableFuture.runAsync(() -> process(task), executor);
            }
        } catch (Exception ex) {
            log.warn("Failed to poll webhook review tasks", ex);
        }
    }

    private void process(WebhookReviewTaskRepository.WebhookReviewTask task) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", task.requestId() == null ? task.id() : task.requestId())) {
            log.info("webhook_task_started id={} platform={} attempt={}", task.id(), task.platform(), task.attemptCount() + 1);
            if ("github".equalsIgnoreCase(task.platform())) {
                processGitHub(task);
            } else if ("gitlab".equalsIgnoreCase(task.platform())) {
                processGitLab(task);
            } else {
                repository.markFailed(task.id(), "unsupported platform: " + task.platform());
                return;
            }
            repository.markSuccess(task.id());
            log.info("webhook_task_completed id={} platform={}", task.id(), task.platform());
        } catch (Exception ex) {
            retryOrFail(task, ex);
        }
    }

    private void processGitHub(WebhookReviewTaskRepository.WebhookReviewTask task) throws Exception {
        JsonNode payload = objectMapper.readTree(task.rawBody());
        var data = githubParser.parse(payload);
        String codeDiff = diffAssembler.assemble(gitHubApiClient.getPrFiles(data.owner(), data.repository(), data.number()));
        ReviewHistoryService.ReviewContext context = ReviewHistoryService.ReviewContext.webhook(
                "GitHub", data.fullName(), data.branch(), data.commitSha(), data.number(), "", data.sourceUrl(), data.title(), codeDiff);
        JayAgentReport report = webhookService.review(codeDiff, data.title(), "general", context);
        try {
            gitHubApiClient.postComment(data.owner(), data.repository(), data.number(), webhookService.buildReviewComment(report, "GitHub"));
        } catch (ExternalApiException ex) {
            log.warn("Failed to post GitHub comment", ex);
        }
    }

    private void processGitLab(WebhookReviewTaskRepository.WebhookReviewTask task) throws Exception {
        JsonNode payload = objectMapper.readTree(task.rawBody());
        var data = gitlabParser.parse(payload);
        String codeDiff = diffAssembler.assemble(gitLabApiClient.getMrChanges(data.projectId(), data.mrIid()));
        ReviewHistoryService.ReviewContext context = ReviewHistoryService.ReviewContext.webhook(
                "GitLab", data.repository(), data.sourceBranch(), data.commitSha(), "", data.mrIid(), data.sourceUrl(), data.sourceBranch(), codeDiff);
        JayAgentReport report = webhookService.review(codeDiff, data.sourceBranch(), "general", context);
        try {
            gitLabApiClient.postComment(data.projectId(), data.mrIid(), webhookService.buildReviewComment(report, "GitLab"));
        } catch (ExternalApiException ex) {
            log.warn("Failed to post GitLab comment", ex);
        }
    }

    private void retryOrFail(WebhookReviewTaskRepository.WebhookReviewTask task, Exception ex) {
        int attempts = task.attemptCount() + 1;
        try {
            if (attempts < 3) {
                repository.markRetry(task.id(), ex.getMessage(), Instant.now().toEpochMilli() + RETRY_DELAY_MS);
                log.warn("Webhook review task retry scheduled id={} attempts={}", task.id(), attempts, ex);
            } else {
                repository.markFailed(task.id(), ex.getMessage());
                log.warn("Webhook review task failed id={} attempts={}", task.id(), attempts, ex);
            }
        } catch (Exception persistEx) {
            log.warn("Failed to update webhook review task state id={}", task.id(), persistEx);
        }
    }
}
