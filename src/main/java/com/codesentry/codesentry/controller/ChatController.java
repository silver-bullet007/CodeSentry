package com.codesentry.codesentry.controller;

import com.codesentry.codesentry.model.CodeReview;
import com.codesentry.codesentry.service.CodeSentryService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final CodeSentryService codeSentryService;
    private final VectorStore vectorStore;

    public ChatController(CodeSentryService codeSentryService, VectorStore vectorStore) {
        this.codeSentryService = codeSentryService;
        this.vectorStore = vectorStore;
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String message,
            @RequestParam(defaultValue = "default-session") String conversationId) {
        return codeSentryService.askAboutCodebase(message, conversationId);
    }

    @PostMapping("/review")
    public CodeReview review(@RequestBody String code) {
        return codeSentryService.reviewCode(code);
    }

    @PostMapping("/ingest")
    public String ingest() {
        List<Document> documents = new ArrayList<>();

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:codebase-source/**/*.java");

            for (Resource resource : resources) {
                String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                String fullPath = resource.getURI().toString();
                String relativePath = fullPath
                        .substring(fullPath.indexOf("codebase-source/") + "codebase-source/".length());
                documents.add(new Document(content, Map.of("source", relativePath)));
            }
        } catch (IOException e) {
            return "Error reading resources: " + e.getMessage();
        }

        TokenTextSplitter splitter = TokenTextSplitter.builder().build();
        List<Document> chunks = splitter.apply(documents);
        vectorStore.add(chunks);

        return "Ingested " + documents.size() + " files as " + chunks.size() + " chunks.";
    }
}