package com.jayagent.jayagent_review.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * AI 对话测试
     * @return
     */
    @GetMapping("/test")
    public String test() {
        return chatClient.prompt()
                .user("用一句话解释什么是代码合规审查")
                .call()
                .content();
    }
}
