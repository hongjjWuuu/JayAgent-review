package com.jayagent.jayagent_review.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiffAssemblerTest {
    @Test void skipsBinaryAndTruncatesLargeDiff() {
        String json = "[{\"filename\":\"image.png\",\"status\":\"added\"},{\"filename\":\"A.java\",\"patch\":\"1234567890\"}]";
        String result = new DiffAssembler(new ObjectMapper(), 5, 20).assemble(json);
        assertFalse(result.contains("image.png"));
        assertTrue(result.contains("A.java"));
        assertTrue(result.contains("[TRUNCATED]"));
    }
}
