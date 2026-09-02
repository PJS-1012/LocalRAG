package com.localai.workspace.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final String model;

    public ChatController(
            ChatService chatService,
            @Value("${spring.ai.ollama.chat.options.model}") String model) {
        this.chatService = chatService;
        this.model = model;
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return new ChatResponse(chatService.chat(request.message()), model);
    }

    public record ChatRequest(@NotBlank String message) {
    }

    public record ChatResponse(String answer, String model) {
    }
}
