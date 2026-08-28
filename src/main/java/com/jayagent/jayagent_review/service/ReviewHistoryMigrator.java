package com.jayagent.jayagent_review.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Component
@DependsOn("reviewHistoryRepository")
public class ReviewHistoryMigrator {

    private static final Logger log = LoggerFactory.getLogger(ReviewHistoryMigrator.class);
    private final ReviewHistoryRepository repository;
    private final ObjectMapper objectMapper;
    private final Path legacyFile;

    public ReviewHistoryMigrator(ReviewHistoryRepository repository, ObjectMapper objectMapper,
                                 @Value("${app.review-history.legacy-file:data/review-history.jsonl}") String legacyFile) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.legacyFile = Paths.get(legacyFile);
    }

    @PostConstruct
    void init() {
        if (!Files.exists(legacyFile) || repository.hasRecords()) {
            return;
        }

        try {
            for (String line : Files.readAllLines(legacyFile, StandardCharsets.UTF_8)) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                ReviewHistoryService.ReviewRecord record = objectMapper.readValue(line, ReviewHistoryService.ReviewRecord.class);
                if (record.getId() == null || record.getId().isBlank()) {
                    record.setId(UUID.randomUUID().toString());
                }
                repository.insert(record);
            }
            log.info("Legacy review history migrated from {}", legacyFile);
        } catch (Exception ex) {
            log.warn("Failed to migrate legacy review history jsonl.", ex);
        }
    }
}
