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
 * GitHub API 客户端.
 */
@Component
public class GitHubApiClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubApiClient.class);
    private static final String GITHUB_BASE_URL = "https://api.github.com";
    private static final String GITHUB_TOKEN = System.getenv("GITHUB_TOKEN");

    private final ExternalApiCallSupport support;
    private final RestClient restClient;
    private final int maxRetries;
    private final long retryBackoffMillis;
    private final int pageSize;
    private final int maxPages;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GitHubApiClient(com.jayagent.jayagent_review.config.JayAgentProperties properties,
                           ObservabilityMetrics metrics) {
        this.support = new ExternalApiCallSupport(metrics);
        this.maxRetries = Math.max(0, properties.getExternalApi().getMaxRetries());
        this.retryBackoffMillis = Math.max(0L, properties.getExternalApi().getRetryBackoffMillis());
        this.pageSize = Math.max(1, properties.getDiff().getPageSize());
        this.maxPages = Math.max(1, properties.getDiff().getMaxPages());
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(GITHUB_BASE_URL)
                .requestFactory(support.createRequestFactory(
                        properties.getExternalApi().getConnectTimeoutMillis(),
                        properties.getExternalApi().getReadTimeoutMillis()));
        if (GITHUB_TOKEN != null && !GITHUB_TOKEN.isBlank()) {
            builder.defaultHeader("Authorization", "token " + GITHUB_TOKEN);
        } else {
            log.warn("GITHUB_TOKEN is not configured. GitHub API access may fail for private repositories.");
        }
        builder.defaultHeader("Accept", "application/vnd.github+json");
        this.restClient = builder.build();
    }

    public String getPrFiles(String owner, String repo, String prNumber) {
        try {
            ArrayNode all = objectMapper.createArrayNode();
            for (int page = 1; page <= maxPages; page++) {
                JsonNode response = objectMapper.readTree(getPrFilesPage(owner, repo, prNumber, page));
                if (!response.isArray() || response.isEmpty()) break;
                all.addAll((ArrayNode) response);
                if (response.size() < pageSize) break;
            }
            return objectMapper.writeValueAsString(all);
        } catch (Exception ex) {
            log.warn("Failed to aggregate GitHub PR pages", ex);
            return "[]";
        }
    }

    private String getPrFilesPage(String owner, String repo, String prNumber, int page) {
        String path = "/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/files";
        return support.executeWithRetry("GitHub", "getPrFiles", owner + "/" + repo + "#" + prNumber,
                maxRetries, retryBackoffMillis, () -> restClient.get()
                .uri(uriBuilder -> uriBuilder.path(path).queryParam("page", page).queryParam("per_page", pageSize).build())
                .retrieve()
                .body(String.class), log);
    }

    public void postComment(String owner, String repo, String prNumber, String commentBody) {
        String path = "/repos/" + owner + "/" + repo + "/issues/" + prNumber + "/comments";
        support.executeWithRetry("GitHub", "postComment", owner + "/" + repo + "#" + prNumber,
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
