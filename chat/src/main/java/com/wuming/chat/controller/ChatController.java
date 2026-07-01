package com.wuming.chat.controller;

import com.wuming.common.web.ApiResponse;
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

    /**
     * 基于可用用户和已完成画像构建的 job 创建一个新的聊天会话。
     */
    @PostMapping("/users/{userId}/sessions/{jobId}")
    public ApiResponse<Long> createSession(
            @PathVariable Long userId,
            @PathVariable Long jobId
    ) {
        Long sessionId = chatService.createSession(userId, jobId);
        return ApiResponse.success(sessionId);
    }

    /**
     * 向指定会话发送用户消息，并返回角色回复。
     */
    @PostMapping("/sessions/{sessionId}/messages")
    public ApiResponse<SendChatMessageResponse> sendMessage(
            @PathVariable Long sessionId,
            @RequestBody SendChatMessageRequest request
    ) {
        return ApiResponse.success(chatService.sendMessage(sessionId, request.getContent()));
    }

    /**
     * 查询会话内已保存的完整消息列表。
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<ChatMessage>> listMessages(@PathVariable Long sessionId) {
        return ApiResponse.success(chatService.listMessages(sessionId));
    }
}
