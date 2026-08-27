package com.codesentry.codesentry.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import com.codesentry.codesentry.service.CodeSentryService;
import com.codesentry.codesentry.tools.FileTools;

@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider mcpTools(FileTools fileTools, @Lazy CodeSentryService codeSentryService) {
        return MethodToolCallbackProvider.builder().toolObjects(fileTools, codeSentryService).build();
    }
}
