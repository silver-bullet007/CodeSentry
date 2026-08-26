package com.codesentry.codesentry.controller;

import com.codesentry.codesentry.model.CodeReview;
import com.codesentry.codesentry.tools.FileTools;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public ChatController(ChatClient.Builder chatClientBuilder, FileTools fileTools, VectorStore vectorStore,
            ChatMemory chatMemory) {
        this.chatClient = chatClientBuilder.defaultTools(fileTools)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder().similarityThreshold(0.75).topK(4).build())
                                .build())
                .build();
        this.vectorStore = vectorStore;
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String message,
            @RequestParam(defaultValue = "default-session") String conversationId) {
        return chatClient.prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(message)
                .call()
                .content();
    }

    @PostMapping("/review")
    public CodeReview review(@RequestBody String code) {

        return chatClient.prompt().system("""
                You are a senior Java code reviewer. Analyze the given code
                snippet for bugs, code smells, and best-practice violations.
                Be specific and concise. If the code is genuinely fine,
                say so — do not invent issues.

                Respond with exactly one JSON object matching the required
                schema. Do not include any text, explanation, or additional
                JSON before or after the object.
                """).user(code)
                .options(GoogleGenAiChatOptions.builder().responseMimeType("application/json"))
                .call().entity(CodeReview.class);

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