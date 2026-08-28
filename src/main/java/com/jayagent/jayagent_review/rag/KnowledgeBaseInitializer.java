package com.jayagent.jayagent_review.rag;

import com.jayagent.jayagent_review.config.JayAgentProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.List;

/**
 * 知识库初始化器。
 * 通过状态文件记录源文档 hash，避免重复启动时反复入库。
 */
@Component
public class KnowledgeBaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseInitializer.class);

    private final DocumentLoader documentLoader;
    private final VectorStore vectorStore;
    private final JayAgentProperties properties;

    public KnowledgeBaseInitializer(DocumentLoader documentLoader,
                                     VectorStore vectorStore,
                                     JayAgentProperties properties) {
        this.documentLoader = documentLoader;
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        if (!properties.getKnowledgeBase().isAutoInit()) {
            log.info("Knowledge base auto init is disabled.");
            return;
        }

        String sourcePath = properties.getKnowledgeBase().getSourcePath();
        Path stateFile = Paths.get(properties.getKnowledgeBase().getStateFile());

        try {
            List<Document> documents = documentLoader.loadAndSplit(sourcePath);
            String sourceHash = sha256(documentLoader.readBytes(sourcePath));
            String previousHash = readPreviousHash(stateFile);

            if (sourceHash.equals(previousHash)) {
                log.info("Knowledge base unchanged, skip reloading. sourcePath={}, hash={}", sourcePath, sourceHash);
                return;
            }

            vectorStore.add(documents);
            writeState(stateFile, sourceHash, documents.size(), sourcePath);
            log.info("Knowledge base initialized, sourcePath={}, chunks={}, hash={}", sourcePath, documents.size(), sourceHash);
        } catch (IOException ex) {
            log.warn("Knowledge base initialization failed, startup will continue without reindexing. sourcePath={}", sourcePath, ex);
        } catch (RuntimeException ex) {
            log.warn("Knowledge base initialization failed, startup will continue without reindexing. sourcePath={}", sourcePath, ex);
        }
    }

    private String readPreviousHash(Path stateFile) {
        try {
            if (!Files.exists(stateFile)) {
                return "";
            }
            List<String> lines = Files.readAllLines(stateFile, StandardCharsets.UTF_8);
            return lines.isEmpty() ? "" : lines.get(0).trim();
        } catch (IOException ex) {
            log.warn("Failed to read knowledge base state file: {}", stateFile, ex);
            return "";
        }
    }

    private void writeState(Path stateFile, String hash, int chunkCount, String sourcePath) {
        try {
            Path parent = stateFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String content = hash + System.lineSeparator()
                    + "sourcePath=" + sourcePath + System.lineSeparator()
                    + "chunkCount=" + chunkCount + System.lineSeparator();
            Files.writeString(stateFile, content, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.warn("Failed to write knowledge base state file: {}", stateFile, ex);
        }
    }

    private String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute knowledge base hash", ex);
        }
    }
}
