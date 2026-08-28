package com.jayagent.jayagent_review.controller;

import com.jayagent.jayagent_review.agent.JayAgentReport;
import com.jayagent.jayagent_review.agent.JayAgentScanner;
import com.jayagent.jayagent_review.controller.dto.JayAgentScanRequest;
import com.jayagent.jayagent_review.service.ReviewHistoryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jayagent.jayagent_review.controller.dto.JayAgentScanResponse;

/**
 * 手动审查接口。
 */
@RestController
@RequestMapping("/api/jayagent")
public class JayAgentController {

    private final JayAgentScanner scanner;
    private final ReviewHistoryService reviewHistoryService;
    private final boolean exposeRawAnalysis;

    public JayAgentController(JayAgentScanner scanner,
                              ReviewHistoryService reviewHistoryService,
                              @Value("${app.security.expose-raw-analysis:false}") boolean exposeRawAnalysis) {
        this.scanner = scanner;
        this.reviewHistoryService = reviewHistoryService;
        this.exposeRawAnalysis = exposeRawAnalysis;
    }

    @PostMapping("/scan")
    public JayAgentScanResponse scan(@Valid @RequestBody JayAgentScanRequest request) {
        JayAgentReport report = scanner.scan(
                request.normalizedCodeDiff(),
                request.normalizedFilePath(),
                request.normalizedReviewScope()
        );
        reviewHistoryService.record("manual", request.normalizedFilePath(), report);

        return JayAgentScanResponse.from(report, exposeRawAnalysis);
    }
}
