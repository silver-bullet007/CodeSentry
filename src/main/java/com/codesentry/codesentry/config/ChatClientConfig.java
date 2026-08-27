package com.codesentry.codesentry.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import com.codesentry.codesentry.tools.FileTools;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder, FileTools fileTools) {
        return chatClientBuilder.defaultTools(fileTools).build();
    }

    @Bean
    public Advisor messageChatmemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    @Bean
    public Advisor questionAnswerAdvisor(VectorStore vectorStore) {

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

        return QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder().similarityThreshold(0.5).topK(4).build())
                .promptTemplate(qaPromptTemplate).build();
    }
}
