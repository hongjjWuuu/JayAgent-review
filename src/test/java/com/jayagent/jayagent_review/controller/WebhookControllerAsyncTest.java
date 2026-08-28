package com.jayagent.jayagent_review.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.jayagent.jayagent_review.config.JayAgentProperties;

import com.jayagent.jayagent_review.service.WebhookReviewOrchestrator;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookControllerAsyncTest {

    @Test
    void githubWebhookReturnsAcceptedImmediately() {
        WebhookController controller = controller();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType("application/json");
        request.addHeader("X-GitHub-Delivery", "delivery-1");
        request.addHeader("X-Hub-Signature-256", "sha256=invalid");

        String body = """
                {
                  "repository": {"full_name": "owner/repo"},
                  "pull_request": {
                    "number": 1,
                    "title": "Add feature",
                    "html_url": "https://example.com/pr/1",
                    "head": {"ref": "main", "sha": "abc123"}
                  }
                }
                """;

        ResponseEntity<Map<String, Object>> response = controller.handleGitHubPR(body, request);
        assertEquals(202, response.getStatusCode().value());
        assertTrue(Boolean.TRUE.equals(response.getBody().get("accepted")));
    }

    @Test
    void gitlabWebhookReturnsAcceptedImmediately() {
        WebhookController controller = controller();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType("application/json");
        request.addHeader("X-Gitlab-Event-UUID", "event-1");
        request.addHeader("X-Gitlab-Token", "token");

        String body = """
                {
                  "project": {"id": 1, "path_with_namespace": "group/repo"},
                  "object_attributes": {
                    "iid": 2,
                    "source_branch": "main",
                    "url": "https://example.com/mr/2",
                    "last_commit": {"id": "abc123"}
                  }
                }
                """;

        ResponseEntity<Map<String, Object>> response = controller.handleGitLabMR(body, request);
        assertEquals(202, response.getStatusCode().value());
        assertTrue(Boolean.TRUE.equals(response.getBody().get("accepted")));
    }

    @Test
    void duplicateGithubWebhookIsAcceptedButNotDispatched() {
        WebhookReviewOrchestrator orchestrator = mock(WebhookReviewOrchestrator.class);
        WebhookController controller = controller(orchestrator, false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType("application/json");
        request.addHeader("X-GitHub-Delivery", "delivery-dup");

        ResponseEntity<Map<String, Object>> response = controller.handleGitHubPR("""
                {"repository":{"full_name":"owner/repo"},"pull_request":{"number":1,"title":"Add feature","html_url":"https://example.com/pr/1","head":{"ref":"main","sha":"abc123"}}}
                """, request);

        assertEquals(202, response.getStatusCode().value());
        assertTrue(Boolean.TRUE.equals(response.getBody().get("duplicate")));
        verify(orchestrator, never()).submitGitHub(any(JsonNode.class), anyString());
    }

    private WebhookController controller() {
        return controller(mock(WebhookReviewOrchestrator.class), true);
    }

    private WebhookController controller(WebhookReviewOrchestrator orchestrator, boolean accepted) {
        JayAgentProperties properties = new JayAgentProperties();
        properties.getWebhook().setStrictMode(false);
        properties.getWebhook().setAllowSharedSecretFallback(true);
        properties.getWebhook().setRequireEventId(true);

        WebhookEventDedupStore dedupStore = mock(WebhookEventDedupStore.class);
        Executor executor = command -> command.run();

        when(dedupStore.markProcessed(anyString())).thenReturn(accepted);

        return new WebhookController(
                orchestrator,
                dedupStore,
                properties,
                new ObjectMapper(),
                "",
                "",
                ""
        );
    }
}
