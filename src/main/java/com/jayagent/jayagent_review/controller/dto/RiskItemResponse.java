package com.jayagent.jayagent_review.controller.dto;

import com.jayagent.jayagent_review.agent.JayAgentReport;

public record RiskItemResponse(
        String level, String category, String ruleId, String location,
        String title, String description, String suggestion, String icon,
        double confidence, String sourceContext, String sourceReference) {

    public static RiskItemResponse from(JayAgentReport.RiskItem risk) {
        return new RiskItemResponse(risk.getLevel(), risk.getCategory(), risk.getRuleId(), risk.getLocation(),
                risk.getTitle(), risk.getDescription(), risk.getSuggestion(), risk.getIcon(), risk.getConfidence(),
                risk.getSourceContext(), risk.getSourceReference());
    }
}
