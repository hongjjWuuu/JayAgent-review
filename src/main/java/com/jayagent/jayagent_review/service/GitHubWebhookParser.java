package com.jayagent.jayagent_review.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class GitHubWebhookParser {
    public GitHubWebhookData parse(JsonNode payload) {
        JsonNode pr = payload == null ? null : payload.path("pull_request");
        JsonNode repository = payload == null ? null : payload.path("repository");
        String fullName = text(repository, "full_name");
        String[] parts = fullName.split("/", 2);
        return new GitHubWebhookData(parts.length > 0 ? parts[0] : "", parts.length > 1 ? parts[1] : "",
                fullName, text(pr, "number"), text(pr, "title"), text(pr.path("head"), "ref"),
                text(pr.path("head"), "sha"), text(pr, "html_url"));
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    public record GitHubWebhookData(String owner, String repository, String fullName, String number,
                                    String title, String branch, String commitSha, String sourceUrl) { }
}
