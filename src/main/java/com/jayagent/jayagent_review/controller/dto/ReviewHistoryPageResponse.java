package com.jayagent.jayagent_review.controller.dto;

import com.jayagent.jayagent_review.service.ReviewHistoryService;
import java.util.List;
import java.util.Map;

public record ReviewHistoryPageResponse(
        boolean success, int page, int size, long total, int totalPages,
        List<ReviewHistoryService.ReviewRecord> items,
        Map<String, Object> stats, List<Map<String, Object>> trend) { }
