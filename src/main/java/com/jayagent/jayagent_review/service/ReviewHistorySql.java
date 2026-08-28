package com.jayagent.jayagent_review.service;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

final class ReviewHistorySql {

    private ReviewHistorySql() {
    }

    static List<Object> buildWhere(StringBuilder sql, ReviewHistoryService.ReviewQuery query) {
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (query.getSource() != null) {
            conditions.add("LOWER(source) = LOWER(?)");
            params.add(query.getSource());
        }
        if (query.getReviewScope() != null) {
            conditions.add("LOWER(review_scope) = LOWER(?)");
            params.add(query.getReviewScope());
        }
        if (query.getFilePath() != null) {
            conditions.add("LOWER(file_path) LIKE LOWER(?)");
            params.add("%" + query.getFilePath() + "%");
        }
        if (query.getHighRisk() != null) {
            conditions.add("high_risk = ?");
            params.add(query.getHighRisk() ? 1 : 0);
        }
        if (query.getMinScore() != null) {
            conditions.add("score >= ?");
            params.add(query.getMinScore());
        }
        if (query.getMaxScore() != null) {
            conditions.add("score <= ?");
            params.add(query.getMaxScore());
        }
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        return params;
    }

    static void bind(PreparedStatement statement, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object value = params.get(i);
            if (value instanceof Integer intValue) {
                statement.setInt(i + 1, intValue);
            } else {
                statement.setString(i + 1, String.valueOf(value));
            }
        }
    }

    static List<ReviewHistoryService.ReviewRecord> readAll(ResultSet resultSet, ReviewHistoryMapper mapper) throws SQLException {
        List<ReviewHistoryService.ReviewRecord> records = new ArrayList<>();
        while (resultSet.next()) {
            records.add(mapper.mapRecord(resultSet));
        }
        return records;
    }
}
