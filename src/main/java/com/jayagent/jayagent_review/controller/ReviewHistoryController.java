package com.jayagent.jayagent_review.controller;

import com.jayagent.jayagent_review.service.ReviewHistoryService;
import com.jayagent.jayagent_review.controller.dto.ReviewHistoryDetailResponse;
import com.jayagent.jayagent_review.controller.dto.ReviewHistoryPageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审查历史与统计接口。
 */
@RestController
@RequestMapping("/api/jayagent")
public class ReviewHistoryController {

    private final ReviewHistoryService reviewHistoryService;
    private final boolean exposeRawAnalysis;

    public ReviewHistoryController(ReviewHistoryService reviewHistoryService,
                                   @Value("${app.security.expose-raw-analysis:false}") boolean exposeRawAnalysis) {
        this.reviewHistoryService = reviewHistoryService;
        this.exposeRawAnalysis = exposeRawAnalysis;
    }

    @GetMapping("/history")
    public ReviewHistoryPageResponse history(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "10") int size,
                                       @RequestParam(required = false) String source,
                                       @RequestParam(required = false) String reviewScope,
                                       @RequestParam(required = false) String filePath,
                                       @RequestParam(required = false) Boolean highRisk,
                                       @RequestParam(required = false) Integer minScore,
                                       @RequestParam(required = false) Integer maxScore) {
        ReviewHistoryService.ReviewQuery query = new ReviewHistoryService.ReviewQuery();
        query.setPage(page);
        query.setSize(size);
        query.setSource(source);
        query.setReviewScope(reviewScope);
        query.setFilePath(filePath);
        query.setHighRisk(highRisk);
        query.setMinScore(minScore);
        query.setMaxScore(maxScore);

        long total = reviewHistoryService.count(query);
        List<ReviewHistoryService.ReviewRecord> items = reviewHistoryService.query(query);
        redactRawAnalysis(items);
        int normalizedPage = Math.max(0, page);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) normalizedSize);

        return new ReviewHistoryPageResponse(true, normalizedPage, normalizedSize, total, totalPages,
                items, reviewHistoryService.stats(), reviewHistoryService.trend());
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("stats", reviewHistoryService.stats());
        return response;
    }

    @GetMapping("/history/{id}")
    public ReviewHistoryDetailResponse detail(@PathVariable String id) {
        ReviewHistoryService.ReviewRecord record = reviewHistoryService.findById(id);
        redactRawAnalysis(record);
        return new ReviewHistoryDetailResponse(true, record);
    }

    private void redactRawAnalysis(List<ReviewHistoryService.ReviewRecord> records) {
        if (!exposeRawAnalysis && records != null) {
            records.forEach(this::redactRawAnalysis);
        }
    }

    private void redactRawAnalysis(ReviewHistoryService.ReviewRecord record) {
        if (!exposeRawAnalysis && record != null) {
            record.setRawAnalysis(null);
        }
    }
}
