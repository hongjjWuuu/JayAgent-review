package com.jayagent.jayagent_review.controller;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索测试接口。
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final VectorStore vectorStore;

    public SearchController(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @GetMapping("/test")
    public List<Map<String, Object>> test(@RequestParam String query) {
        return vectorStore.similaritySearch(query)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private Map<String, Object> toResponse(Document doc) {
        Map<String, Object> map = new HashMap<>();
        map.put("content", doc.getText());
        map.put("score", doc.getScore());
        map.put("id", doc.getId());
        map.put("metadata", doc.getMetadata());
        map.put("source", doc.getMetadata() == null ? null : doc.getMetadata().get("source"));
        map.put("chunkIndex", doc.getMetadata() == null ? null : doc.getMetadata().get("chunkIndex"));
        map.put("ruleId", doc.getMetadata() == null ? null : doc.getMetadata().get("ruleId"));
        map.put("summary", buildSummary(doc));
        return map;
    }

    private String buildSummary(Document doc) {
        StringBuilder sb = new StringBuilder();
        if (doc.getId() != null) {
            sb.append("id=").append(doc.getId()).append("; ");
        }
        if (doc.getScore() != null) {
            sb.append("score=").append(doc.getScore()).append("; ");
        }
        if (doc.getMetadata() != null && !doc.getMetadata().isEmpty()) {
            Object source = doc.getMetadata().get("source");
            Object ruleId = doc.getMetadata().get("ruleId");
            Object chunkIndex = doc.getMetadata().get("chunkIndex");
            if (source != null) {
                sb.append("source=").append(source).append("; ");
            }
            if (ruleId != null) {
                sb.append("ruleId=").append(ruleId).append("; ");
            }
            if (chunkIndex != null) {
                sb.append("chunkIndex=").append(chunkIndex).append("; ");
            }
        }
        return sb.length() == 0 ? "" : sb.toString().trim();
    }
}
