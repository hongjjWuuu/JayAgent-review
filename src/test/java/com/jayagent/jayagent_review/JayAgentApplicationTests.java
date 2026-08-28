package com.jayagent.jayagent_review;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = {
        "spring.ai.ollama.chat.enabled=false",
        "spring.ai.ollama.embedding.enabled=false",
        "spring.ai.model.embedding=openai",
        "app.security.api-key-enabled=false",
        "app.knowledge-base.auto-init=false",
        "spring.elasticsearch.uris=http://localhost:9",
        "spring.autoconfigure.exclude=org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration,org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration,org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration"
})
class JayAgentApplicationTests {

	@MockBean
	private VectorStore vectorStore;

	@MockBean
	private ChatClient.Builder chatClientBuilder;

	@MockBean
	private ChatClient chatClient;

	@Test
	void contextLoads() {
	}

}
