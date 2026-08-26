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
import org.springframework.ai.chat.prompt.PromptTemplate;

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
import org.springframework.ai.chat.client.advisor.api.Advisor;
import com.codesentry.codesentry.model.RagDecision;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final Advisor questionAnswerAdvisor;
    private final Advisor messageChatMemoryAdvisor;

    public ChatController(ChatClient.Builder chatClientBuilder, FileTools fileTools, VectorStore vectorStore,
            ChatMemory chatMemory) {

        PromptTemplate qaPromptTemplate = PromptTemplate.builder()
                .template("""
                        You are an assistant for a Java codebase. You have access to
                        relevant code context below and the ongoing conversation history.

                        - If the question is about the codebase, answer using the
                          context below. If the context doesn't cover it, say so.
                        - If the question is conversational (e.g. about something
                          said earlier, like a name), answer normally using the
                          conversation history — you do not need code context for that.

                        Code context:
                        {question_answer_context}

                        Question: {query}
                        """)
                .build();
        this.messageChatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        this.questionAnswerAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder().similarityThreshold(0.5).topK(4).build())
                .promptTemplate(qaPromptTemplate).build();
        this.chatClient = chatClientBuilder.defaultTools(fileTools)
                .build();
        this.vectorStore = vectorStore;
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String message,
            @RequestParam(defaultValue = "default-session") String conversationId) {

        RagDecision requireRetrieval = chatClient.prompt().system("""
                You are a classifier. Decide if answering the following
                message well requires looking up specific implementation
                details from a Java codebase...
                Respond with exactly YES or NO.
                """).user(message).options(GoogleGenAiChatOptions.builder().temperature(0.0)).call()
                .entity(RagDecision.class);

        var promptSpec = chatClient.prompt()
                .advisors(messageChatMemoryAdvisor)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));

        if (requireRetrieval == RagDecision.YES) {
            promptSpec.advisors(questionAnswerAdvisor);
        }

        return promptSpec.user(message).call().content();
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