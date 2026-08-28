package com.jayagent.jayagent_review.rag;

import com.jayagent.jayagent_review.config.JayAgentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class KnowledgeBaseInitializerTest {

    @Test
    void initContinuesWhenLoaderFails() throws IOException {
        DocumentLoader loader = mock(DocumentLoader.class);
        VectorStore vectorStore = mock(VectorStore.class);
        JayAgentProperties properties = new JayAgentProperties();
        properties.getKnowledgeBase().setAutoInit(true);

        doThrow(new IOException("boom")).when(loader).loadAndSplit("classpath:knowledge/java_security_rules.txt");

        KnowledgeBaseInitializer initializer = new KnowledgeBaseInitializer(loader, vectorStore, properties);

        assertDoesNotThrow(initializer::init);
        verifyNoInteractions(vectorStore);
    }

    @Test
    void initCanBeDisabled() {
        DocumentLoader loader = mock(DocumentLoader.class);
        VectorStore vectorStore = mock(VectorStore.class);
        JayAgentProperties properties = new JayAgentProperties();
        properties.getKnowledgeBase().setAutoInit(false);

        KnowledgeBaseInitializer initializer = new KnowledgeBaseInitializer(loader, vectorStore, properties);

        assertDoesNotThrow(initializer::init);
        verifyNoInteractions(loader);
        verifyNoInteractions(vectorStore);
    }
}
