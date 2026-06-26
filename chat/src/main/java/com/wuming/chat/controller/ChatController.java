package com.wuming.chat.controller;

import com.wuming.chat.domain.dto.ApiResponse;
import com.wuming.chat.domain.dto.CreateChatSessionRequest;
import com.wuming.chat.domain.dto.CreateChatSessionResponse;
import com.wuming.chat.domain.dto.SendChatMessageRequest;
import com.wuming.chat.domain.dto.SendChatMessageResponse;
import com.wuming.chat.domain.entity.ChatMessage;
import com.wuming.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;

    @PostMapping("/sessions")
    public ApiResponse<CreateChatSessionResponse> createSession(
            @RequestBody CreateChatSessionRequest request
    ) {
        Long sessionId = chatService.createSession(request.getJobId());
        return ApiResponse.success(new CreateChatSessionResponse(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ApiResponse<SendChatMessageResponse> sendMessage(
            @PathVariable Long sessionId,
            @RequestBody SendChatMessageRequest request
    ) {
        return ApiResponse.success(chatService.sendMessage(sessionId, request.getContent()));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<ChatMessage>> listMessages(@PathVariable Long sessionId) {
        return ApiResponse.success(chatService.listMessages(sessionId));
    }
}
