package com.localai.workspace.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ChatClient chatClient;
    private final int agentContextWindow;

    public ChatService(ChatClient.Builder chatClientBuilder,
            @Value("${localrag.agent.context-window:16384}") int agentContextWindow) {
        this.chatClient = chatClientBuilder.build();
        this.agentContextWindow = agentContextWindow;
    }

    public String chat(String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    public String chat(String systemMessage, String userMessage) {
        return chatClient.prompt()
                .system(systemMessage)
                .user(userMessage)
                .call()
                .content();
    }

    public String chatWithTools(String systemMessage, String userMessage, Object... tools) {
        return chatClient.prompt()
                .system(systemMessage)
                .user(userMessage)
                .tools(tools)
                .call()
                .content();
    }

    public String chatWithToolCallbacks(String systemMessage, String userMessage,
            org.springframework.ai.tool.ToolCallback... callbacks) {
        return chatClient.prompt().system(systemMessage).user(userMessage)
                .options(OllamaChatOptions.builder().numCtx(agentContextWindow).build())
                .toolCallbacks(callbacks).call().content();
    }
}
