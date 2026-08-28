package com.jayagent.jayagent_review.controller.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JayAgentScanRequestTest {

    @Test
    void shouldNormalizeBlankValues() {
        JayAgentScanRequest request = new JayAgentScanRequest();

        assertEquals("", request.normalizedCodeDiff());
        assertEquals("unknown", request.normalizedFilePath());
        assertEquals("general", request.normalizedReviewScope());
    }

    @Test
    void shouldReturnProvidedValues() {
        JayAgentScanRequest request = new JayAgentScanRequest();
        request.setCodeDiff("diff");
        request.setFilePath("src/App.java");
        request.setReviewScope("backend");

        assertEquals("diff", request.normalizedCodeDiff());
        assertEquals("src/App.java", request.normalizedFilePath());
        assertEquals("backend", request.normalizedReviewScope());
    }
}
