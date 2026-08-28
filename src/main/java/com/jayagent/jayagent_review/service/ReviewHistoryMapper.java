package com.jayagent.jayagent_review.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayagent.jayagent_review.agent.JayAgentReport;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class ReviewHistoryMapper {

    private static final TypeReference<List<JayAgentReport.RiskItem>> RISK_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public ReviewHistoryMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ReviewHistoryService.ReviewRecord mapRecord(ResultSet resultSet) throws SQLException {
        ReviewHistoryService.ReviewRecord record = new ReviewHistoryService.ReviewRecord();
        record.setId(resultSet.getString("id"));
        record.setCreatedAt(textToInstant(resultSet.getString("created_at")));
        record.setSource(resultSet.getString("source"));
        record.setPlatform(resultSet.getString("platform"));
        record.setRepository(resultSet.getString("repository"));
        record.setBranch(resultSet.getString("branch"));
        record.setCommitSha(resultSet.getString("commit_sha"));
        record.setPrNumber(resultSet.getString("pr_number"));
        record.setMrIid(resultSet.getString("mr_iid"));
        record.setSourceUrl(resultSet.getString("source_url"));
        record.setDiffHash(resultSet.getString("diff_hash"));
        record.setReviewScope(resultSet.getString("review_scope"));
        record.setFilePath(resultSet.getString("file_path"));
        record.setScore(resultSet.getInt("score"));
        record.setHighRisk(resultSet.getInt("high_risk") == 1);
        record.setSummary(resultSet.getString("summary"));
        record.setRiskCount(resultSet.getInt("risk_count"));
        record.setCriticalCount(resultSet.getInt("critical_count"));
        record.setHighCount(resultSet.getInt("high_count"));
        record.setAnalysisLatencyMs(resultSet.getLong("analysis_latency_ms"));
        record.setRawAnalysis(resultSet.getString("raw_analysis"));
        record.setRisks(fromJson(resultSet.getString("risks_json")));
        return record;
    }

    public String toJson(List<JayAgentReport.RiskItem> risks) {
        try {
            return objectMapper.writeValueAsString(risks == null ? List.of() : risks);
        } catch (Exception ex) {
            return "[]";
        }
    }

    public List<JayAgentReport.RiskItem> fromJson(String value) {
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(value, RISK_LIST_TYPE);
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    public Instant textToInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return java.sql.Timestamp.valueOf(value).toInstant();
        }
    }
}
