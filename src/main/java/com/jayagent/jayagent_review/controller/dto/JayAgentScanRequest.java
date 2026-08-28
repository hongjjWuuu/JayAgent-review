package com.jayagent.jayagent_review.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 手动审查请求体。
 */
public class JayAgentScanRequest {

    @NotBlank(message = "codeDiff is required")
    @Size(max = 20000, message = "codeDiff is too long")
    private String codeDiff;

    @Size(max = 500, message = "filePath is too long")
    private String filePath;

    @Size(max = 100, message = "reviewScope is too long")
    private String reviewScope;

    public String getCodeDiff() {
        return codeDiff;
    }

    public void setCodeDiff(String codeDiff) {
        this.codeDiff = codeDiff;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getReviewScope() {
        return reviewScope;
    }

    public void setReviewScope(String reviewScope) {
        this.reviewScope = reviewScope;
    }

    public String normalizedCodeDiff() {
        return codeDiff == null ? "" : codeDiff;
    }

    public String normalizedFilePath() {
        return (filePath == null || filePath.isBlank()) ? "unknown" : filePath;
    }

    public String normalizedReviewScope() {
        return (reviewScope == null || reviewScope.isBlank()) ? "general" : reviewScope;
    }
}
