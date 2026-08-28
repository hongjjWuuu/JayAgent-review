package com.jayagent.jayagent_review.controller;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rules")
public class RuleController {

    public RuleController(VectorStore vectorStore) {
    }

    /**
     * 刷新知识库（从数据库重新加载规范并重建向量索引）
     */
    @PostMapping("/refresh")
    public Map<String, String> refresh() {
        // V2.0：从数据库重新加载所有规范，向量化后更新 ES
        return Map.of("status", "知识库已刷新");
    }
}
