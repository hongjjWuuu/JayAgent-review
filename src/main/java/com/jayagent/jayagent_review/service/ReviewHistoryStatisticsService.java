package com.jayagent.jayagent_review.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class ReviewHistoryStatisticsService {

    private static final Logger log = LoggerFactory.getLogger(ReviewHistoryStatisticsService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private final ReviewHistoryRepository repository;

    public ReviewHistoryStatisticsService(ReviewHistoryRepository repository) {
        this.repository = repository;
    }

    public synchronized Map<String, Object> stats() {
        String sql = """
                SELECT
                    COUNT(*) AS total_reviews,
                    SUM(CASE WHEN high_risk = 1 THEN 1 ELSE 0 END) AS high_risk_reviews,
                    AVG(score) AS average_score,
                    SUM(critical_count) AS critical_risks,
                    SUM(high_count) AS high_risks,
                    MAX(created_at) AS latest_review_at
                FROM review_history
                """;
        Map<String, Object> stats = new LinkedHashMap<>();
        try (var connection = repository.connect();
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery(sql)) {
            if (resultSet.next()) {
                double avgScore = resultSet.getDouble("average_score");
                stats.put("totalReviews", resultSet.getLong("total_reviews"));
                stats.put("highRiskReviews", resultSet.getLong("high_risk_reviews"));
                stats.put("averageScore", Math.round(avgScore * 10.0) / 10.0);
                stats.put("criticalRisks", resultSet.getLong("critical_risks"));
                stats.put("highRisks", resultSet.getLong("high_risks"));
                stats.put("latestReviewAt", formatInstant(resultSet.getString("latest_review_at")));
            }
        } catch (Exception ex) {
            log.warn("Failed to build review history stats.", ex);
        }
        return stats;
    }

    public synchronized List<Map<String, Object>> trend() {
        String sql = "SELECT created_at, score, high_risk, review_scope, source FROM review_history ORDER BY created_at ASC";
        try (var connection = repository.connect();
             var statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            List<Map<String, Object>> trend = new ArrayList<>();
            while (resultSet.next()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("createdAt", resultSet.getString("created_at"));
                item.put("score", resultSet.getInt("score"));
                item.put("highRisk", resultSet.getInt("high_risk") == 1);
                item.put("reviewScope", resultSet.getString("review_scope"));
                item.put("source", resultSet.getString("source"));
                trend.add(item);
            }
            return trend;
        } catch (Exception ex) {
            log.warn("Failed to build review history trend.", ex);
            return List.of();
        }
    }

    private String formatInstant(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return TIME_FORMATTER.format(java.time.Instant.parse(value));
        } catch (Exception ex) {
            try {
                return TIME_FORMATTER.format(java.sql.Timestamp.valueOf(value).toInstant());
            } catch (Exception ignored) {
                return value;
            }
        }
    }
}
