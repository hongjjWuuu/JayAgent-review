package com.jayagent.jayagent_review.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

@Repository
public class ReviewHistoryRepository {

    private final Path databaseFile;
    private final ReviewHistoryMapper mapper;

    public ReviewHistoryRepository(ReviewHistoryMapper mapper,
                                   @Value("${app.review-history.database-file:data/review-history.db}") String databaseFile) {
        this.mapper = mapper;
        this.databaseFile = Paths.get(databaseFile);
    }

    @PostConstruct
    void init() {
        try {
            Path parent = databaseFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Connection connection = connect()) {
                initSchema(connection);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize review history repository", ex);
        }
    }

    public synchronized void insert(ReviewHistoryService.ReviewRecord record) throws SQLException {
        try (Connection connection = connect()) {
            insert(connection, record);
        }
    }

    public synchronized List<ReviewHistoryService.ReviewRecord> query(ReviewHistoryService.ReviewQuery query) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM review_history");
        List<Object> params = ReviewHistorySql.buildWhere(sql, query);
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(query.getLimit());
        params.add(query.getOffset());

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            ReviewHistorySql.bind(statement, params);
            return ReviewHistorySql.readAll(statement.executeQuery(), mapper);
        }
    }

    public synchronized long count(ReviewHistoryService.ReviewQuery query) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM review_history");
        List<Object> params = ReviewHistorySql.buildWhere(sql, query);

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            ReviewHistorySql.bind(statement, params);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0;
            }
        }
    }

    public synchronized ReviewHistoryService.ReviewRecord findById(String id) throws SQLException {
        if (id == null || id.isBlank()) {
            return null;
        }
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM review_history WHERE id = ?")) {
            statement.setString(1, id);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapper.mapRecord(resultSet) : null;
            }
        }
    }

    public synchronized boolean hasRecords() {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT COUNT(*) FROM review_history")) {
            return resultSet.next() && resultSet.getLong(1) > 0;
        } catch (SQLException ex) {
            return true;
        }
    }

    Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath());
    }

    private void initSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS review_history (
                        id TEXT PRIMARY KEY,
                        created_at TEXT NOT NULL,
                        source TEXT,
                        platform TEXT,
                        repository TEXT,
                        branch TEXT,
                        commit_sha TEXT,
                        pr_number TEXT,
                        mr_iid TEXT,
                        source_url TEXT,
                        diff_hash TEXT,
                        review_scope TEXT,
                        file_path TEXT,
                        score INTEGER,
                        high_risk INTEGER,
                        summary TEXT,
                        risk_count INTEGER,
                        critical_count INTEGER,
                        high_count INTEGER,
                        analysis_latency_ms INTEGER,
                        raw_analysis TEXT,
                        risks_json TEXT
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_review_history_created_at ON review_history(created_at)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_review_history_source ON review_history(source)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_review_history_review_scope ON review_history(review_scope)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_review_history_file_path ON review_history(file_path)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_review_history_high_risk ON review_history(high_risk)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_review_history_platform ON review_history(platform)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_review_history_repository ON review_history(repository)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_review_history_commit_sha ON review_history(commit_sha)");
        }
    }

    private void insert(Connection connection, ReviewHistoryService.ReviewRecord record) throws SQLException {
        String sql = """
                INSERT OR REPLACE INTO review_history (
                    id, created_at, source, platform, repository, branch, commit_sha, pr_number, mr_iid,
                    source_url, diff_hash, review_scope, file_path, score, high_risk, summary, risk_count,
                    critical_count, high_count, analysis_latency_ms, raw_analysis, risks_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.getId());
            statement.setString(2, record.getCreatedAt() == null ? null : record.getCreatedAt().toString());
            statement.setString(3, record.getSource());
            statement.setString(4, record.getPlatform());
            statement.setString(5, record.getRepository());
            statement.setString(6, record.getBranch());
            statement.setString(7, record.getCommitSha());
            statement.setString(8, record.getPrNumber());
            statement.setString(9, record.getMrIid());
            statement.setString(10, record.getSourceUrl());
            statement.setString(11, record.getDiffHash());
            statement.setString(12, record.getReviewScope());
            statement.setString(13, record.getFilePath());
            statement.setInt(14, record.getScore());
            statement.setInt(15, record.isHighRisk() ? 1 : 0);
            statement.setString(16, record.getSummary());
            statement.setInt(17, record.getRiskCount());
            statement.setInt(18, record.getCriticalCount());
            statement.setInt(19, record.getHighCount());
            statement.setLong(20, record.getAnalysisLatencyMs());
            statement.setString(21, record.getRawAnalysis());
            statement.setString(22, mapper.toJson(record.getRisks()));
            statement.executeUpdate();
        }
    }
}
