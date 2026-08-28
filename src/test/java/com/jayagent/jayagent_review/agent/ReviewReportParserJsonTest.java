package com.jayagent.jayagent_review.agent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReviewReportParserJsonTest {
    @Test void parsesStructuredJson() {
        JayAgentReport report = new JayAgentReport();
        ReviewReportParser.parse("{\"summary\":\"found issue\",\"risks\":[{\"level\":\"HIGH\",\"title\":\"token\",\"confidence\":0.92}]}", report);
        assertEquals("found issue", report.getSummary());
        assertEquals(1, report.getRisks().size());
        assertEquals(0.92, report.getRisks().get(0).getConfidence(), 0.001);
    }

    @Test void invalidJsonFallsBackToLegacyProtocol() {
        JayAgentReport report = new JayAgentReport();
        ReviewReportParser.parse("{invalid\nSUMMARY|safe", report);
        assertEquals("safe", report.getSummary());
        assertTrue(report.getRisks().isEmpty());
    }
}
