package com.codesentry.codesentry.controller;

import com.codesentry.codesentry.model.CodeReview;
import com.codesentry.codesentry.service.CodeSentryService;

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
        Path baseDir = Paths.get("src/main/java/com/codesentry").toAbsolutePath().normalize();

        List<Document> documents = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(baseDir)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path);
                            String relativePath = baseDir.relativize(path).toString();
                            Document doc = new Document(content, Map.of("source", relativePath));
                            documents.add(doc);
                        } catch (IOException e) {
                            // skip unreadable files
                        }
                    });
        } catch (IOException e) {
            return "Error walking directory: " + e.getMessage();
        }

        TokenTextSplitter splitter = TokenTextSplitter.builder().build();
        List<Document> chunks = splitter.apply(documents);

        vectorStore.add(chunks);

        return "Ingested " + documents.size() + " files as " + chunks.size() + " chunks.";
    }
}