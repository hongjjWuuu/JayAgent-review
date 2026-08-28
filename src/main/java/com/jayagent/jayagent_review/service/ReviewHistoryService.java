package com.jayagent.jayagent_review.service;

import com.jayagent.jayagent_review.agent.JayAgentReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 审查历史持久化与统计服务。
 */
@Service
public class ReviewHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ReviewHistoryService.class);
    private final ReviewHistoryRepository repository;
    private final ReviewHistoryStatisticsService statisticsService;
    private final boolean persistRawAnalysis;

    public ReviewHistoryService(ReviewHistoryRepository repository,
                                ReviewHistoryStatisticsService statisticsService,
                                @org.springframework.beans.factory.annotation.Value("${app.security.persist-raw-analysis:false}") boolean persistRawAnalysis) {
        this.repository = repository;
        this.statisticsService = statisticsService;
        this.persistRawAnalysis = persistRawAnalysis;
    }

    public ReviewRecord record(String source, String filePath, JayAgentReport report) {
        return record(ReviewContext.basic(source, filePath), report);
    }

    public ReviewRecord record(ReviewContext context, JayAgentReport report) {
        ReviewRecord record = ReviewRecord.from(context, report);
        if (!persistRawAnalysis) {
            record.setRawAnalysis(null);
        }
        try {
            repository.insert(record);
        } catch (SQLException ex) {
            log.warn("Failed to persist review history.", ex);
        }
        return record;
    }

    public List<ReviewRecord> recent(int limit) {
        ReviewQuery query = new ReviewQuery();
        query.setSize(limit);
        return query(query);
    }

    public List<ReviewRecord> query(ReviewQuery query) {
        ReviewQuery safeQuery = query == null ? ReviewQuery.empty() : query.normalized();
        try {
            return repository.query(safeQuery);
        } catch (SQLException ex) {
            log.warn("Failed to query review history.", ex);
            return List.of();
        }
    }

    public long count(ReviewQuery query) {
        ReviewQuery safeQuery = query == null ? ReviewQuery.empty() : query.normalized();
        try {
            return repository.count(safeQuery);
        } catch (SQLException ex) {
            log.warn("Failed to count review history.", ex);
            return 0;
        }
    }

    public Map<String, Object> stats() {
        return statisticsService.stats();
    }

    public List<Map<String, Object>> trend() {
        return statisticsService.trend();
    }

    public List<ReviewRecord> all() {
        ReviewQuery query = new ReviewQuery();
        query.setSize(100);
        return query(query);
    }

    public ReviewRecord findById(String id) {
        try {
            return repository.findById(id);
        } catch (SQLException ex) {
            log.warn("Failed to find review history detail: {}", id, ex);
            return null;
        }
    }

    public static class ReviewQuery {
        private String source;
        private String reviewScope;
        private String filePath;
        private Boolean highRisk;
        private Integer minScore;
        private Integer maxScore;
        private int page = 0;
        private int size = 10;
        private int offset = 0;
        private int limit = 10;

        public static ReviewQuery empty() {
            return new ReviewQuery();
        }

        public ReviewQuery normalized() {
            ReviewQuery query = new ReviewQuery();
            query.source = blankToNull(source);
            query.reviewScope = blankToNull(reviewScope);
            query.filePath = blankToNull(filePath);
            query.highRisk = highRisk;
            query.minScore = minScore;
            query.maxScore = maxScore;
            query.size = Math.max(1, Math.min(size <= 0 ? 10 : size, 100));
            query.page = Math.max(0, page);
            query.offset = query.page * query.size;
            query.limit = query.size;
            return query;
        }

        private String blankToNull(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getReviewScope() {
            return reviewScope;
        }

        public void setReviewScope(String reviewScope) {
            this.reviewScope = reviewScope;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public Boolean getHighRisk() {
            return highRisk;
        }

        public void setHighRisk(Boolean highRisk) {
            this.highRisk = highRisk;
        }

        public Integer getMinScore() {
            return minScore;
        }

        public void setMinScore(Integer minScore) {
            this.minScore = minScore;
        }

        public Integer getMaxScore() {
            return maxScore;
        }

        public void setMaxScore(Integer maxScore) {
            this.maxScore = maxScore;
        }

        public int getPage() {
            return page;
        }

        public void setPage(int page) {
            this.page = page;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public int getOffset() {
            return offset;
        }

        public void setOffset(int offset) {
            this.offset = offset;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }
    }

    public static class ReviewRecord {
        private String id;
        private Instant createdAt;
        private String source;
        private String platform;
        private String repository;
        private String branch;
        private String commitSha;
        private String prNumber;
        private String mrIid;
        private String sourceUrl;
        private String diffHash;
        private String reviewScope;
        private String filePath;
        private int score;
        private boolean highRisk;
        private String summary;
        private int riskCount;
        private int criticalCount;
        private int highCount;
        private long analysisLatencyMs;
        private String rawAnalysis;
        private List<JayAgentReport.RiskItem> risks = new ArrayList<>();

        public static ReviewRecord from(String source, String filePath, JayAgentReport report) {
            return from(ReviewContext.basic(source, filePath), report);
        }

        public static ReviewRecord from(ReviewContext context, JayAgentReport report) {
            ReviewContext safeContext = context == null ? ReviewContext.basic("manual", "unknown") : context;
            ReviewRecord record = new ReviewRecord();
            record.id = UUID.randomUUID().toString();
            record.createdAt = Instant.now();
            record.source = blankToDefault(safeContext.getSource(), "manual");
            record.platform = blankToDefault(safeContext.getPlatform(), record.source);
            record.repository = blankToDefault(safeContext.getRepository(), "");
            record.branch = blankToDefault(safeContext.getBranch(), "");
            record.commitSha = blankToDefault(safeContext.getCommitSha(), "");
            record.prNumber = blankToDefault(safeContext.getPrNumber(), "");
            record.mrIid = blankToDefault(safeContext.getMrIid(), "");
            record.sourceUrl = blankToDefault(safeContext.getSourceUrl(), "");
            record.diffHash = blankToDefault(safeContext.getDiffHash(), "");
            record.reviewScope = report == null || report.getReviewScope() == null ? "general" : report.getReviewScope();
            record.filePath = blankToDefault(safeContext.getFilePath(), "unknown");
            record.score = report == null ? 100 : report.getScore();
            record.highRisk = report != null && report.isHighRisk();
            record.summary = report == null ? "" : report.getSummary();
            record.riskCount = report == null || report.getRisks() == null ? 0 : report.getRisks().size();
            record.analysisLatencyMs = report == null ? 0 : report.getAnalysisLatencyMs();
            record.rawAnalysis = report == null ? "" : report.getRawAnalysis();
            if (report != null && report.getRisks() != null) {
                record.risks = new ArrayList<>(report.getRisks());
            }
            if (report != null && report.getRisks() != null) {
                for (JayAgentReport.RiskItem risk : report.getRisks()) {
                    String level = risk.getLevel() == null ? "" : risk.getLevel().trim().toUpperCase();
                    if ("CRITICAL".equals(level)) {
                        record.criticalCount++;
                    } else if ("HIGH".equals(level)) {
                        record.highCount++;
                    }
                }
            }
            return record;
        }

        private static String blankToDefault(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getPlatform() { return platform; }
        public void setPlatform(String platform) { this.platform = platform; }
        public String getRepository() { return repository; }
        public void setRepository(String repository) { this.repository = repository; }
        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }
        public String getCommitSha() { return commitSha; }
        public void setCommitSha(String commitSha) { this.commitSha = commitSha; }
        public String getPrNumber() { return prNumber; }
        public void setPrNumber(String prNumber) { this.prNumber = prNumber; }
        public String getMrIid() { return mrIid; }
        public void setMrIid(String mrIid) { this.mrIid = mrIid; }
        public String getSourceUrl() { return sourceUrl; }
        public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
        public String getDiffHash() { return diffHash; }
        public void setDiffHash(String diffHash) { this.diffHash = diffHash; }
        public String getReviewScope() { return reviewScope; }
        public void setReviewScope(String reviewScope) { this.reviewScope = reviewScope; }
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }
        public boolean isHighRisk() { return highRisk; }
        public void setHighRisk(boolean highRisk) { this.highRisk = highRisk; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public int getRiskCount() { return riskCount; }
        public void setRiskCount(int riskCount) { this.riskCount = riskCount; }
        public int getCriticalCount() { return criticalCount; }
        public void setCriticalCount(int criticalCount) { this.criticalCount = criticalCount; }
        public int getHighCount() { return highCount; }
        public void setHighCount(int highCount) { this.highCount = highCount; }
        public long getAnalysisLatencyMs() { return analysisLatencyMs; }
        public void setAnalysisLatencyMs(long analysisLatencyMs) { this.analysisLatencyMs = analysisLatencyMs; }
        public String getRawAnalysis() { return rawAnalysis; }
        public void setRawAnalysis(String rawAnalysis) { this.rawAnalysis = rawAnalysis; }
        public List<JayAgentReport.RiskItem> getRisks() { return risks; }
        public void setRisks(List<JayAgentReport.RiskItem> risks) { this.risks = risks; }
    }

    public static class ReviewContext {
        private String source;
        private String platform;
        private String repository;
        private String branch;
        private String commitSha;
        private String prNumber;
        private String mrIid;
        private String sourceUrl;
        private String diffHash;
        private String filePath;

        public static ReviewContext basic(String source, String filePath) {
            ReviewContext context = new ReviewContext();
            context.source = source;
            context.platform = source;
            context.filePath = filePath;
            return context;
        }

        public static ReviewContext webhook(String platform, String repository, String branch, String commitSha,
                                            String prNumber, String mrIid, String sourceUrl, String filePath,
                                            String codeDiff) {
            ReviewContext context = basic("webhook", filePath);
            context.platform = platform;
            context.repository = repository;
            context.branch = branch;
            context.commitSha = commitSha;
            context.prNumber = prNumber;
            context.mrIid = mrIid;
            context.sourceUrl = sourceUrl;
            context.diffHash = sha256(codeDiff);
            return context;
        }

        private static String sha256(String value) {
            if (value == null || value.isBlank()) {
                return "";
            }
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
                StringBuilder hex = new StringBuilder(bytes.length * 2);
                for (byte b : bytes) {
                    hex.append(String.format("%02x", b));
                }
                return hex.toString();
            } catch (NoSuchAlgorithmException ex) {
                return "";
            }
        }

        public String getSource() { return source; }
        public String getPlatform() { return platform; }
        public String getRepository() { return repository; }
        public String getBranch() { return branch; }
        public String getCommitSha() { return commitSha; }
        public String getPrNumber() { return prNumber; }
        public String getMrIid() { return mrIid; }
        public String getSourceUrl() { return sourceUrl; }
        public String getDiffHash() { return diffHash; }
        public String getFilePath() { return filePath; }
    }
}
