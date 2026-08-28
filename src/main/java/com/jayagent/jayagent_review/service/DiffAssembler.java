package com.jayagent.jayagent_review.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 将 GitHub/GitLab 文件变更响应统一组装为审查 Diff。 */
@Component
public class DiffAssembler {
    private static final Logger log = LoggerFactory.getLogger(DiffAssembler.class);
    private final int maxFileChars;
    private final int maxTotalChars;
    private final ObjectMapper objectMapper;
    public DiffAssembler(ObjectMapper objectMapper) { this(objectMapper, 20000, 100000); }
    @Autowired
    public DiffAssembler(ObjectMapper objectMapper, com.jayagent.jayagent_review.config.JayAgentProperties properties) {
        this(objectMapper, properties.getDiff().getMaxFileChars(), properties.getDiff().getMaxTotalChars());
    }
    public DiffAssembler(ObjectMapper objectMapper, int maxFileChars, int maxTotalChars) {
        this.objectMapper = objectMapper;
        this.maxFileChars = Math.max(1, maxFileChars);
        this.maxTotalChars = Math.max(1, maxTotalChars);
    }

    public String assemble(String filesJson) {
        if (filesJson == null || filesJson.isBlank()) return "";
        try {
            JsonNode root = objectMapper.readTree(filesJson);
            StringBuilder diff = new StringBuilder();
            JsonNode files = root.isArray() ? root : root.path("files");
            if (files.isArray()) {
                for (JsonNode file : files) {
                    if (diff.length() >= maxTotalChars) { log.warn("Diff truncated at total limit {}", maxTotalChars); break; }
                    append(diff, file);
                }
            } else {
                append(diff, root);
            }
            return diff.toString();
        } catch (Exception ex) {
            return filesJson;
        }
    }

    private void append(StringBuilder output, JsonNode file) {
        if (file == null || file.isNull()) return;
        String name = first(file, "filename", "file_name", "path", "new_path", "old_path");
        String patch = first(file, "patch", "diff");
        if (patch.isBlank()) return;
        if (patch.length() > maxFileChars) {
            log.warn("Diff truncated for file {} at limit {}", name, maxFileChars);
            patch = patch.substring(0, maxFileChars) + "\n[TRUNCATED]";
        }
        if (output.length() + patch.length() > maxTotalChars) {
            int remaining = Math.max(0, maxTotalChars - output.length());
            if (remaining == 0) return;
            patch = patch.substring(0, Math.min(remaining, patch.length())) + "\n[TRUNCATED]";
        }
        output.append("### ").append(name.isBlank() ? "unknown-file" : name).append('\n')
                .append(patch).append('\n');
    }

    private String first(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && !value.asText("").isBlank()) return value.asText("");
        }
        return "";
    }
}
