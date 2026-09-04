package com.localai.workspace.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
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
}
