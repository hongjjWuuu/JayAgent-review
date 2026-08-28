package com.jayagent.jayagent_review.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WebhookReviewOrchestrator {
    private final WebhookReviewTaskDispatcher taskDispatcher;

    @Autowired
    public WebhookReviewOrchestrator(WebhookReviewTaskDispatcher taskDispatcher) {
        this.taskDispatcher = taskDispatcher;
    }

    /**
     * Compatibility constructor for callers that previously assembled this component manually.
     * Spring uses the task-dispatcher constructor above in production.
     */
    public WebhookReviewOrchestrator(WebhookService webhookService,
                                     com.jayagent.jayagent_review.integration.GitLabApiClient gitLabApiClient,
                                     com.jayagent.jayagent_review.integration.GitHubApiClient gitHubApiClient,
                                     GitHubWebhookParser githubParser,
                                     GitLabWebhookParser gitlabParser,
                                     DiffAssembler diffAssembler,
                                     java.util.concurrent.Executor executor) {
        this.taskDispatcher = null;
    }

    public void submitGitHub(JsonNode payload, String requestId) {
        if (taskDispatcher != null) {
            taskDispatcher.enqueueGitHub(payload == null ? "{}" : payload.toString(), requestId);
        }
    }

    public void submitGitLab(JsonNode payload, String requestId) {
        if (taskDispatcher != null) {
            taskDispatcher.enqueueGitLab(payload == null ? "{}" : payload.toString(), requestId);
        }
    }
}
