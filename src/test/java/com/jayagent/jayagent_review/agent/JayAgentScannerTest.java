package com.jayagent.jayagent_review.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;

import com.jayagent.jayagent_review.config.JayAgentProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JayAgentScannerTest {

    @Test
    void shouldParseSummaryAndRiskLines() throws Exception {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        when(builder.build()).thenReturn(chatClient);

        VectorStore vectorStore = mock(VectorStore.class);
        JayAgentProperties properties = new JayAgentProperties();
        properties.getKnowledgeBase().setMinScore(0.3);
        JayAgentScanner scanner = new JayAgentScanner(builder, vectorStore, properties);
        JayAgentReport report = parse("""
                SUMMARY|Potential secret exposure detected
                RISK|HIGH|src/App.java:12|Hardcoded token|Token is embedded in source|Move it to a secure secret store
                """);

        assertEquals("Potential secret exposure detected", report.getSummary());
        assertEquals(1, report.getRisks().size());
        assertEquals("HIGH", report.getRisks().get(0).getLevel());
        assertEquals("src/App.java:12", report.getRisks().get(0).getLocation());
    }

    @Test
    void shouldFallbackToPlainTextSummary() throws Exception {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        when(builder.build()).thenReturn(chatClient);

        VectorStore vectorStore = mock(VectorStore.class);
        JayAgentProperties properties = new JayAgentProperties();
        properties.getKnowledgeBase().setMinScore(0.3);
        JayAgentScanner scanner = new JayAgentScanner(builder, vectorStore, properties);

        JayAgentReport report = parse("""
                No obvious review risk found.
                Additional explanation from model.
                """);

        assertEquals("No obvious review risk found.", report.getSummary());
    }

    private JayAgentReport parse(String result) {
        JayAgentReport report = new JayAgentReport();
        ReviewReportParser.parse(result, report);
        return report;
    }
}
