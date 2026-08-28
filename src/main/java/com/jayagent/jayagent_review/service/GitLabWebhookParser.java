package com.jayagent.jayagent_review.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class GitLabWebhookParser {
    public GitLabWebhookData parse(JsonNode payload) {
        JsonNode project = payload == null ? null : payload.path("project");
        JsonNode attributes = payload == null ? null : payload.path("object_attributes");
        JsonNode lastCommit = attributes == null ? null : attributes.path("last_commit");
        String repository = first(project, "path_with_namespace", "name");
        String commit = text(lastCommit, "id");
        return new GitLabWebhookData(text(project, "id"), repository, text(attributes, "iid"),
                text(attributes, "source_branch"), commit, first(attributes, "url"));
    }

    private String first(JsonNode node, String... fields) {
        for (String field : fields) { String value = text(node, field); if (!value.isBlank()) return value; }
        return "";
    }
    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    public record GitLabWebhookData(String projectId, String repository, String mrIid,
                                    String sourceBranch, String commitSha, String sourceUrl) { }
}
