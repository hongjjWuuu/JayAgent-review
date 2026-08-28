package com.jayagent.jayagent_review.agent;

import com.jayagent.jayagent_review.config.RiskScoringConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 审查报告 DTO。
 */
public class JayAgentReport {

    private String rawAnalysis;
    private String reviewScope;
    private String summary;
    private String knowledgeContext;
    private long analysisLatencyMs;
    private List<RiskItem> risks = new ArrayList<>();

    public int getScore() {
        if (risks.isEmpty()) {
            return 100;
        }

        int penalty = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        double duplicateFactor = Math.max(0.0, Math.min(1.0, RiskScoringConfig.getScoring().getDuplicateRiskFactor()));
        double confidenceWeight = Math.max(0.0, Math.min(1.0, RiskScoringConfig.getScoring().getConfidenceWeight()));
        for (RiskItem risk : risks) {
            String level = normalizeLevel(risk.getLevel());
            int base;
            if ("CRITICAL".equals(level)) {
                base = RiskScoringConfig.getScoring().getCriticalPenalty();
            } else if ("HIGH".equals(level)) {
                base = RiskScoringConfig.getScoring().getHighPenalty();
            } else if ("MEDIUM".equals(level)) {
                base = RiskScoringConfig.getScoring().getMediumPenalty();
            } else if ("LOW".equals(level)) {
                base = RiskScoringConfig.getScoring().getLowPenalty();
            } else {
                base = 0;
            }
            String key = level + "|" + risk.getRuleId() + "|" + risk.getLocation() + "|" + risk.getTitle();
            double repeatMultiplier = seen.add(key) ? 1.0 : duplicateFactor;
            // Legacy text risks have no confidence field; preserve their historical score.
            double confidence = risk.getConfidence() <= 0.0 ? 1.0
                    : Math.max(0.0, Math.min(1.0, risk.getConfidence()));
            double confidenceMultiplier = (1.0 - confidenceWeight) + confidenceWeight * confidence;
            penalty += (int) Math.round(base * repeatMultiplier * confidenceMultiplier);
        }
        return Math.max(0, 100 - penalty);
    }

    public boolean isHighRisk() {
        return risks.stream().anyMatch(r -> {
            String level = normalizeLevel(r.getLevel());
            return "CRITICAL".equals(level) || "HIGH".equals(level);
        });
    }

    public String getRawAnalysis() {
        return rawAnalysis;
    }

    public void setRawAnalysis(String rawAnalysis) {
        this.rawAnalysis = rawAnalysis;
    }

    public String getReviewScope() {
        return reviewScope;
    }

    public void setReviewScope(String reviewScope) {
        this.reviewScope = reviewScope;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getKnowledgeContext() {
        return knowledgeContext;
    }

    public void setKnowledgeContext(String knowledgeContext) {
        this.knowledgeContext = knowledgeContext;
    }

    public List<RiskItem> getRisks() {
        return risks;
    }

    public void setRisks(List<RiskItem> risks) {
        this.risks = risks;
    }

    public long getAnalysisLatencyMs() {
        return analysisLatencyMs;
    }

    public void setAnalysisLatencyMs(long analysisLatencyMs) {
        this.analysisLatencyMs = analysisLatencyMs;
    }

    private String normalizeLevel(String level) {
        return level == null ? "" : level.trim().toUpperCase();
    }

    public static class RiskItem {
        private String level;
        private String category;
        private String ruleId;
        private String location;
        private String title;
        private String description;
        private String suggestion;
        private String icon;
        private double confidence;
        private String sourceContext;
        private String sourceReference;

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getRuleId() {
            return ruleId;
        }

        public void setRuleId(String ruleId) {
            this.ruleId = ruleId;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getSuggestion() {
            return suggestion;
        }

        public void setSuggestion(String suggestion) {
            this.suggestion = suggestion;
        }

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        public double getConfidence() {
            return confidence;
        }

        public void setConfidence(double confidence) {
            this.confidence = confidence;
        }

        public String getSourceContext() {
            return sourceContext;
        }

        public void setSourceContext(String sourceContext) {
            this.sourceContext = sourceContext;
        }

        public String getSourceReference() {
            return sourceReference;
        }

        public void setSourceReference(String sourceReference) {
            this.sourceReference = sourceReference;
        }
    }
}
