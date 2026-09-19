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
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import java.io.File;
import java.util.Comparator;

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
public String ingest(@RequestParam String repoUrl) {
    Path tempDir;
    try {
        tempDir = Files.createTempDirectory("codesentry-clone-");
    } catch (IOException e) {
        return "Error creating temp directory: " + e.getMessage();
    }

    try {
        Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(tempDir.toFile())
                .setDepth(1)
                .call();
    } catch (GitAPIException e) {
        deleteRecursively(tempDir);
        return "Error cloning repository: " + e.getMessage();
    }

    List<Document> documents = new ArrayList<>();
    long totalSize = 0;
    final long MAX_TOTAL_BYTES = 20 * 1024 * 1024; // 20MB cap
    final int MAX_FILES = 500;

    try (Stream<Path> paths = Files.walk(tempDir)) {
        List<Path> matched = paths
                .filter(Files::isRegularFile)
                .filter(p -> !p.toString().contains(File.separator + ".git" + File.separator))
                .filter(p -> hasAllowedExtension(p))
                .limit(MAX_FILES)
                .toList();

        for (Path path : matched) {
            long size = Files.size(path);
            if (totalSize + size > MAX_TOTAL_BYTES) {
                break;
            }
            totalSize += size;

            try {
                String content = Files.readString(path);
                String relativePath = tempDir.relativize(path).toString();
                documents.add(new Document(content, Map.of("source", relativePath, "corpus", "current")));
            } catch (IOException e) {
                // skip unreadable/binary files
            }
        }
    } catch (IOException e) {
        deleteRecursively(tempDir);
        return "Error walking cloned repo: " + e.getMessage();
    }

    deleteRecursively(tempDir);

    if (documents.isEmpty()) {
        return "No ingestible files found in repository.";
    }

    vectorStore.delete("corpus == 'current'");
    TokenTextSplitter splitter = TokenTextSplitter.builder().build();
    List<Document> chunks = splitter.apply(documents);
    final int BATCH_SIZE = 40;
    final long DELAY_MS = 15000; // 15 seconds between batches, safely under 100/min

    for (int i = 0; i < chunks.size(); i += BATCH_SIZE) {
        List<Document> batch = chunks.subList(i, Math.min(i + BATCH_SIZE, chunks.size()));
        vectorStore.add(batch);

        if (i + BATCH_SIZE < chunks.size()) {
            try {
                Thread.sleep(DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "Ingestion interrupted.";
            }
        }
    }

    return "Ingested " + documents.size() + " files as " + chunks.size() + " chunks from " + repoUrl;
}

private boolean hasAllowedExtension(Path path) {
    String name = path.toString().toLowerCase();
    return name.endsWith(".java") || name.endsWith(".py") || name.endsWith(".js")
            || name.endsWith(".ts") || name.endsWith(".md") || name.endsWith(".json")
            || name.endsWith(".yaml") || name.endsWith(".yml");
}

private void deleteRecursively(Path dir) {
    try (Stream<Path> paths = Files.walk(dir)) {
        paths.sorted(Comparator.reverseOrder()).forEach(p -> {
            try {
                Files.delete(p);
            } catch (IOException ignored) {
            }
        });
    } catch (IOException ignored) {
    }
}
}