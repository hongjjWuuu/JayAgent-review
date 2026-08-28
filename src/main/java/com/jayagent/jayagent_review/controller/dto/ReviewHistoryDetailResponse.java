package com.jayagent.jayagent_review.controller.dto;

import com.jayagent.jayagent_review.service.ReviewHistoryService;

public record ReviewHistoryDetailResponse(boolean success, ReviewHistoryService.ReviewRecord item) { }
