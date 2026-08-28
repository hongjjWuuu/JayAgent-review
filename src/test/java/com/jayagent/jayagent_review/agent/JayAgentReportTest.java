package com.jayagent.jayagent_review.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JayAgentReportTest {

    @Test
    void shouldComputeScoreFromRiskLevels() {
        JayAgentReport report = new JayAgentReport();

        addRisk(report, newRiskItem("CRITICAL"));
        addRisk(report, newRiskItem("HIGH"));

        assertEquals(65, report.getScore());
        assertTrue(report.isHighRisk());
    }

    @Test
    void shouldTreatEmptyReportAsSafe() {
        JayAgentReport report = new JayAgentReport();

        assertEquals(100, report.getScore());
        assertFalse(report.isHighRisk());
    }

    private Object newRiskItem(String level) {
        try {
            Class<?> type = Class.forName("com.jayagent.jayagent_review.agent.JayAgentReport$RiskItem");
            Object riskItem = type.getDeclaredConstructor().newInstance();
            type.getMethod("setLevel", String.class).invoke(riskItem, level);
            return riskItem;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create RiskItem for test", ex);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void addRisk(JayAgentReport report, Object riskItem) {
        ((java.util.List) report.getRisks()).add(riskItem);
    }
}
