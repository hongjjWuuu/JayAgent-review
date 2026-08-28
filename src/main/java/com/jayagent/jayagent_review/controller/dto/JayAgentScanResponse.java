package com.jayagent.jayagent_review.controller.dto;

import com.jayagent.jayagent_review.agent.JayAgentReport;
import java.util.List;

public record JayAgentScanResponse(
        boolean success, String reviewScope, int score, boolean highRisk,
        List<RiskItemResponse> risks, String summary, String rawAnalysis) {
    public static JayAgentScanResponse from(JayAgentReport report, boolean includeRawAnalysis) {
        List<RiskItemResponse> items = report.getRisks() == null ? List.of() : report.getRisks().stream()
                .map(RiskItemResponse::from).toList();
        return new JayAgentScanResponse(true, report.getReviewScope(), report.getScore(), report.isHighRisk(),
                items, report.getSummary(), includeRawAnalysis ? report.getRawAnalysis() : null);
    }
}
