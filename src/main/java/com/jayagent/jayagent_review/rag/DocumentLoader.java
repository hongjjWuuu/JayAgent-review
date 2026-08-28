package com.jayagent.jayagent_review.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 文档加载器
 */
@Component
public class DocumentLoader {

    // Spring 内置资源加载工具，用来读取 resources 下文件；
    private final ResourceLoader resourceLoader;

    public DocumentLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public List<Document> loadAndSplit(String path) throws IOException {
        Resource resource = resolve(path);

         // 2. 文本读取器加载文件内容
        TextReader reader = new TextReader(resource);
        List<Document> documents = reader.get();

         // 3. Token级文本分割器
        TokenTextSplitter splitter = new TokenTextSplitter();
        // 4. 执行切分，返回多个小块Document
        return splitter.apply(documents);
    }

    public byte[] readBytes(String path) throws IOException {
        Resource resource = resolve(path);
        try (InputStream inputStream = resource.getInputStream()) {
            return inputStream.readAllBytes();
        }
    }

    private Resource resolve(String path) throws IOException {
        if (path == null || path.isBlank()) {
            throw new IOException("Knowledge base source path is blank");
        }

        String location = path;
        Path filePath = null;
        if (path.matches("^[A-Za-z]:[\\\\/].*")) {
            filePath = Paths.get(path);
        } else if (!path.contains(":")) {
            filePath = Paths.get(path);
        }
        if (filePath != null && filePath.isAbsolute()) {
            location = filePath.toUri().toString();
        } else if (filePath != null && !path.startsWith("/")) {
            location = "classpath:" + path;
        }

        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IOException("Knowledge base source does not exist: " + path);
        }
        return resource;
    }
}
