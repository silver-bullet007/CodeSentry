package com.codesentry.codesentry.controller;

import com.codesentry.codesentry.model.CodeReview;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String message) {
        return chatClient.prompt()
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
                """).user(code).call().entity(CodeReview.class);

    }
}