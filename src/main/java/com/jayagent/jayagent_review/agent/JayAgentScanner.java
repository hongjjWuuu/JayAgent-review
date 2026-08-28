package com.jayagent.jayagent_review.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.jayagent.jayagent_review.config.JayAgentProperties;
import com.jayagent.jayagent_review.observability.ObservabilityMetrics;

/**
* 核心 JayAgent 扫描服务。
* 将知识库检索与大语言模型分析相结合。
*/
@Service
public class JayAgentScanner {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final JayAgentProperties properties;
    private final ObservabilityMetrics metrics;

    @Autowired
    public JayAgentScanner(ChatClient.Builder builder, VectorStore vectorStore, JayAgentProperties properties,
                           ObservabilityMetrics metrics) {
        this.chatClient = builder.build();
        this.vectorStore = vectorStore;
        this.properties = properties;
        this.metrics = metrics;
    }

    public JayAgentScanner(ChatClient.Builder builder, VectorStore vectorStore, JayAgentProperties properties) {
        this(builder, vectorStore, properties,
                new ObservabilityMetrics(io.micrometer.core.instrument.Metrics.globalRegistry));
    }

    public JayAgentReport scan(String codeDiff, String filePath, String reviewScope) {
        long startedAt = System.nanoTime();
        String safeCodeDiff = codeDiff == null ? "" : codeDiff;
        String safeFilePath = filePath == null ? "unknown" : filePath;
        String safeReviewScope = (reviewScope == null || reviewScope.isBlank()) ? "general" : reviewScope;

        String searchQuery = ReviewPromptBuilder.buildSearchQuery(safeCodeDiff, safeFilePath);

        long ragStarted = System.nanoTime();
        List<Document> documents;
        try {
            documents = vectorStore.similaritySearch(searchQuery);
            metrics.ragSearch(System.nanoTime() - ragStarted, true);
        } catch (RuntimeException ex) {
            metrics.ragSearch(System.nanoTime() - ragStarted, false);
            throw ex;
        }
        if (documents == null) {
            documents = List.of();
        }
        String context = ReviewPromptBuilder.buildContext(documents, properties.getKnowledgeBase().getMinScore());

        String systemPrompt = ReviewPromptBuilder.buildSystemPrompt(safeReviewScope, context);

        long llmStarted = System.nanoTime();
        String analysisResult;
        try {
            analysisResult = chatClient.prompt().system(systemPrompt)
                    .user("Please analyze the following review risks in the code diff:\n\n```java\n" + safeCodeDiff + "\n```")
                    .call().content();
            metrics.llmCall(System.nanoTime() - llmStarted, true);
        } catch (RuntimeException ex) {
            metrics.llmCall(System.nanoTime() - llmStarted, false);
            throw ex;
        }

        JayAgentReport report = new JayAgentReport();
        report.setReviewScope(safeReviewScope);
        report.setKnowledgeContext(context);
        report.setRawAnalysis(analysisResult);
        ReviewReportParser.parse(analysisResult, report);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        report.setAnalysisLatencyMs(elapsedMs);
        return report;
    }

}
