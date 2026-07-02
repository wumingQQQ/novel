package com.wuming.chat.controller;

import com.wuming.chat.domain.dto.SendChatMessageRequest;
import com.wuming.chat.domain.dto.SendChatMessageResponse;
import com.wuming.chat.domain.entity.ChatMessage;
import com.wuming.chat.service.ChatService;
import com.wuming.common.security.JwtUserIdExtractor;
import com.wuming.common.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
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
    private final JwtUserIdExtractor jwtUserIdExtractor;

    /**
     * 基于当前登录用户和已完成画像构建的 job 创建一个新的聊天会话。
     */
    @PostMapping("/sessions/{jobId}")
    public ApiResponse<Long> createSession(
            @PathVariable Long jobId,
            Authentication authentication
    ) {
        Long userId = jwtUserIdExtractor.requireUserId(authentication);
        Long sessionId = chatService.createSession(userId, jobId);
        return ApiResponse.success(sessionId);
    }

    /**
     * 向当前用户拥有的会话发送消息，并返回角色回复。
     */
    @PostMapping("/sessions/{sessionId}/messages")
    public ApiResponse<SendChatMessageResponse> sendMessage(
            @PathVariable Long sessionId,
            @RequestBody SendChatMessageRequest request,
            Authentication authentication
    ) {
        Long userId = jwtUserIdExtractor.requireUserId(authentication);
        return ApiResponse.success(chatService.sendMessage(
                userId,
                sessionId,
                request.getContent()
        ));
    }

    /**
     * 查询当前用户拥有的会话内已保存的完整消息列表。
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<ChatMessage>> listMessages(
            @PathVariable Long sessionId,
            Authentication authentication
    ) {
        Long userId = jwtUserIdExtractor.requireUserId(authentication);
        return ApiResponse.success(chatService.listMessages(userId, sessionId));
    }
}
