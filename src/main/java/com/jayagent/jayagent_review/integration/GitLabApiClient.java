package com.jayagent.jayagent_review.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;


import java.util.Map;
import com.jayagent.jayagent_review.observability.ObservabilityMetrics;

/**
 * GitLab API client.
 */
@Component
public class GitLabApiClient {

    private static final Logger log = LoggerFactory.getLogger(GitLabApiClient.class);
    private static final String GITLAB_BASE_URL = "https://gitlab.com/api/v4";
    private static final String GITLAB_TOKEN = System.getenv("GITLAB_TOKEN");

    private final ExternalApiCallSupport support;
    private final RestClient restClient;
    private final int maxRetries;
    private final long retryBackoffMillis;
    private final int pageSize;
    private final int maxPages;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GitLabApiClient(com.jayagent.jayagent_review.config.JayAgentProperties properties,
                           ObservabilityMetrics metrics) {
        this.support = new ExternalApiCallSupport(metrics);
        this.maxRetries = Math.max(0, properties.getExternalApi().getMaxRetries());
        this.retryBackoffMillis = Math.max(0L, properties.getExternalApi().getRetryBackoffMillis());
        this.pageSize = Math.max(1, properties.getDiff().getPageSize());
        this.maxPages = Math.max(1, properties.getDiff().getMaxPages());
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(GITLAB_BASE_URL)
                .requestFactory(support.createRequestFactory(
                        properties.getExternalApi().getConnectTimeoutMillis(),
                        properties.getExternalApi().getReadTimeoutMillis()));
        if (GITLAB_TOKEN != null && !GITLAB_TOKEN.isBlank()) {
            builder.defaultHeader("PRIVATE-TOKEN", GITLAB_TOKEN);
        } else {
            log.warn("GITLAB_TOKEN is not configured. GitLab API access may fail for private repositories.");
        }
        this.restClient = builder.build();
    }

    public String getMrChanges(String projectId, String mrIid) {
        try {
            JsonNode root = null;
            ArrayNode all = objectMapper.createArrayNode();
            for (int page = 1; page <= maxPages; page++) {
                JsonNode response = objectMapper.readTree(getMrChangesPage(projectId, mrIid, page));
                if (root == null) root = response.deepCopy();
                JsonNode changes = response.path("changes");
                if (!changes.isArray() || changes.isEmpty()) break;
                all.addAll((ArrayNode) changes);
                if (changes.size() < pageSize) break;
            }
            if (root == null || !root.isObject()) return "{}";
            ((com.fasterxml.jackson.databind.node.ObjectNode) root).set("changes", all);
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            log.warn("Failed to aggregate GitLab MR pages", ex);
            return "{}";
        }
    }

    private String getMrChangesPage(String projectId, String mrIid, int page) {
        String path = "/projects/" + projectId + "/merge_requests/" + mrIid + "/changes";
        return support.executeWithRetry("GitLab", "getMrChanges", projectId + "#" + mrIid,
                maxRetries, retryBackoffMillis, () -> restClient.get()
                .uri(uriBuilder -> uriBuilder.path(path).queryParam("page", page).queryParam("per_page", pageSize).build())
                .retrieve()
                .body(String.class), log);
    }

    public void postComment(String projectId, String mrIid, String commentBody) {
        String path = "/projects/" + projectId + "/merge_requests/" + mrIid + "/notes";
        support.executeWithRetry("GitLab", "postComment", projectId + "#" + mrIid,
                maxRetries, retryBackoffMillis, () -> {
            restClient.post()
                    .uri(path)
                    .body(Map.of("body", commentBody))
                    .retrieve()
                    .toBodilessEntity();
            return null;
        }, log);
    }
}
