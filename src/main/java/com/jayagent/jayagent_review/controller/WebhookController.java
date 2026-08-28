package com.jayagent.jayagent_review.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayagent.jayagent_review.config.JayAgentProperties;
import com.jayagent.jayagent_review.service.WebhookReviewOrchestrator;
import com.jayagent.jayagent_review.service.WebhookSecurityService;
import com.jayagent.jayagent_review.observability.ObservabilityMetrics;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/webhook")
public class WebhookController {
    private final WebhookEventDedupStore dedupStore;
    private final ObjectMapper objectMapper;
    private final WebhookSecurityService securityService;
    private final WebhookReviewOrchestrator reviewOrchestrator;
    private final ObservabilityMetrics metrics;

    @Autowired
    public WebhookController(WebhookReviewOrchestrator reviewOrchestrator,
                             WebhookEventDedupStore dedupStore, JayAgentProperties properties,
                             ObjectMapper objectMapper,
                             @Value("${app.jayagent.webhook.shared-secret:}") String sharedSecret,
                             @Value("${app.jayagent.webhook.github-secret:}") String githubSecret,
                             @Value("${app.jayagent.webhook.gitlab-token:}") String gitlabToken,
                             ObservabilityMetrics metrics) {
        this.dedupStore = dedupStore;
        this.objectMapper = objectMapper;
        this.securityService = new WebhookSecurityService(properties, sharedSecret, githubSecret, gitlabToken);
        this.metrics = metrics;
        this.reviewOrchestrator = reviewOrchestrator;
    }

    public WebhookController(WebhookReviewOrchestrator reviewOrchestrator,
                             WebhookEventDedupStore dedupStore, JayAgentProperties properties,
                             ObjectMapper objectMapper,
                             String sharedSecret, String githubSecret, String gitlabToken) {
        this(reviewOrchestrator, dedupStore, properties, objectMapper, sharedSecret, githubSecret, gitlabToken,
                new com.jayagent.jayagent_review.observability.ObservabilityMetrics(
                        io.micrometer.core.instrument.Metrics.globalRegistry));
    }

    public WebhookController(com.jayagent.jayagent_review.service.WebhookService webhookService,
                             com.jayagent.jayagent_review.integration.GitLabApiClient gitLabApiClient,
                             com.jayagent.jayagent_review.integration.GitHubApiClient gitHubApiClient,
                             WebhookEventDedupStore dedupStore, JayAgentProperties properties,
                             ObjectMapper objectMapper, java.util.concurrent.Executor webhookExecutor,
                             String sharedSecret, String githubSecret, String gitlabToken) {
        this(new WebhookReviewOrchestrator(webhookService, gitLabApiClient, gitHubApiClient,
                        new com.jayagent.jayagent_review.service.GitHubWebhookParser(),
                        new com.jayagent.jayagent_review.service.GitLabWebhookParser(),
                        new com.jayagent.jayagent_review.service.DiffAssembler(objectMapper, properties),
                        webhookExecutor),
                dedupStore, properties, objectMapper, sharedSecret, githubSecret, gitlabToken);
    }

    @PostMapping("/gitlab")
    public ResponseEntity<Map<String, Object>> handleGitLabMR(@RequestBody String rawBody, HttpServletRequest request) {
        metrics.webhookReceived("gitlab");
        try { securityService.validateGitLab(rawBody, request); }
        catch (ResponseStatusException ex) { metrics.webhookRejected("gitlab", String.valueOf(ex.getStatusCode().value())); throw ex; }
        JsonNode payload = readJson(rawBody);
        String eventKey = firstNonBlank(request.getHeader("X-Gitlab-Event-UUID"), request.getHeader("X-Request-Id"));
        boolean duplicate = !markEventProcessed("gitlab", eventKey, rawBody);
        if (duplicate) metrics.webhookDuplicate("gitlab");
        if (!duplicate) reviewOrchestrator.submitGitLab(payload, correlationId(request));
        return ResponseEntity.accepted().body(acceptedResponse("gitlab", duplicate));
    }

    @PostMapping("/github")
    public ResponseEntity<Map<String, Object>> handleGitHubPR(@RequestBody String rawBody, HttpServletRequest request) {
        metrics.webhookReceived("github");
        try { securityService.validateGitHub(rawBody, request); }
        catch (ResponseStatusException ex) { metrics.webhookRejected("github", String.valueOf(ex.getStatusCode().value())); throw ex; }
        JsonNode payload = readJson(rawBody);
        String eventKey = firstNonBlank(request.getHeader("X-GitHub-Delivery"), request.getHeader("X-Request-Id"));
        boolean duplicate = !markEventProcessed("github", eventKey, rawBody);
        if (duplicate) metrics.webhookDuplicate("github");
        if (!duplicate) reviewOrchestrator.submitGitHub(payload, correlationId(request));
        return ResponseEntity.accepted().body(acceptedResponse("github", duplicate));
    }

    private Map<String, Object> acceptedResponse(String platform, boolean duplicate) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true); response.put("platform", platform); response.put("accepted", true);
        response.put("duplicate", duplicate);
        response.put("message", duplicate ? "duplicate webhook ignored" : "review accepted and scheduled");
        return response;
    }

    private JsonNode readJson(String rawBody) {
        try { return objectMapper.readTree(rawBody == null ? "" : rawBody); }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON payload", ex); }
    }

    private boolean markEventProcessed(String platform, String eventKey, String rawBody) {
        String dedupeKey = platform + ":" + firstNonBlank(eventKey, sha256(rawBody == null ? "" : rawBody));
        return dedupStore.markProcessed(dedupeKey);
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception ex) { throw new IllegalStateException("Failed to compute webhook deduplication hash", ex); }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(com.jayagent.jayagent_review.config.CorrelationIdFilter.MDC_KEY);
        return value == null ? request.getHeader(com.jayagent.jayagent_review.config.CorrelationIdFilter.HEADER) : value.toString();
    }
}
