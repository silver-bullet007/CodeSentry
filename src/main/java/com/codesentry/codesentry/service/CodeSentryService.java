package com.codesentry.codesentry.service;

import com.codesentry.codesentry.model.CodeReview;
import com.codesentry.codesentry.model.RagDecision;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class CodeSentryService {

    private final ChatClient chatClient;
    private final ChatClient classifierChatClient;
    private final Advisor questionAnswerAdvisor;
    private final Advisor messageChatMemoryAdvisor;

    public CodeSentryService(@Qualifier("chatClient") ChatClient chatClient,
            @Qualifier("classifierChatClient") ChatClient classifierChatClient,
            @Qualifier("questionAnswerAdvisor") Advisor questionAnswerAdvisor,
            @Qualifier("messageChatmemoryAdvisor") Advisor messageChatMemoryAdvisor) {
        this.classifierChatClient = classifierChatClient;
        this.chatClient = chatClient;
        this.questionAnswerAdvisor = questionAnswerAdvisor;
        this.messageChatMemoryAdvisor = messageChatMemoryAdvisor;
    }

    @Tool(description = "Ask a question about the CodeSentry Java codebase. Automatically retrieves relevant code context when needed and remembers conversation history.")
    public String askAboutCodebase(
            @ToolParam(description = "The question to ask") String message,
            @ToolParam(description = "A unique ID to keep this conversation's history separate from others") String conversationId) {

        RagDecision decision = classifierChatClient.prompt()
                .system("""
                        You are a classifier. Decide if answering the following
                        message well requires looking up specific implementation
                        details from a Java codebase (e.g. how something is
                        implemented, what a class/method does, code structure).
                        Casual, conversational, or general messages do not need this.
                        """)
                .user(message)
                .options(GoogleGenAiChatOptions.builder()
                        .temperature(0.0)
                        .responseMimeType("application/json"))
                .call()
                .entity(RagDecision.class);

        var promptSpec = chatClient.prompt()
                .advisors(messageChatMemoryAdvisor)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));

        if (decision == RagDecision.YES) {
            promptSpec = promptSpec.advisors(questionAnswerAdvisor);
        }

        return promptSpec.user(message).call().content();
    }

    @Tool(description = "Review a Java code snippet for bugs, code smells, and best-practice violations. Returns a structured review with severity-rated issues and an overall rating.")
    public CodeReview reviewCode(@ToolParam(description = "The Java code snippet to review") String code) {
        return chatClient.prompt()
                .system("""
                        You are a senior Java code reviewer. Analyze the given code
                        snippet for bugs, code smells, and best-practice violations.
                        Be specific and concise. If the code is genuinely fine,
                        say so — do not invent issues.

                        Respond with exactly one JSON object matching the required
                        schema. Do not include any text, explanation, or additional
                        JSON before or after the object.
                        """)
                .user(code)
                .options(GoogleGenAiChatOptions.builder().responseMimeType("application/json"))
                .call()
                .entity(CodeReview.class);
    }
}