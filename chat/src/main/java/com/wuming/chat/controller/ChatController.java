package com.wuming.chat.controller;

import com.wuming.chat.domain.dto.CreateChatSessionResponse;
import com.wuming.chat.domain.dto.SendChatMessageRequest;
import com.wuming.chat.domain.dto.SendChatMessageResponse;
import com.wuming.chat.domain.dto.CreateChatSessionRequest;
import com.wuming.chat.domain.dto.ChatSessionSummary;
import com.wuming.chat.domain.entity.ChatMessage;
import com.wuming.chat.service.ChatService;
import com.wuming.chat.sse.ChatSseEmitterService;
import com.wuming.common.security.JwtUserIdExtractor;
import com.wuming.common.web.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;
    private final ChatSseEmitterService chatSseEmitterService;
    private final JwtUserIdExtractor jwtUserIdExtractor;

    /**
     * 创建聊天会话。
     *
     * <p>公共角色和个人版本角色统一使用该入口：userRoleVersionId为空时创建公共角色会话，
     * 非空时创建绑定当前用户个人角色版本的会话。</p>
     */
    @PostMapping("/sessions")
    public ApiResponse<CreateChatSessionResponse> createVersionedSession(
            @Valid @RequestBody CreateChatSessionRequest request, Authentication authentication) {
        Long userId = jwtUserIdExtractor.requireUserId(authentication);
        Long sessionId = chatService.createSession(
                userId, request.getCharacterId(), request.getUserRoleVersionId());
        return ApiResponse.success(CreateChatSessionResponse.of(
                sessionId, request.getCharacterId(), request.getUserRoleVersionId()));
    }

    /** 查询当前用户的聊天会话摘要。 */
    @GetMapping("/sessions")
    public ApiResponse<List<ChatSessionSummary>> listSessions(Authentication authentication) {
        return ApiResponse.success(chatService.listSessions(jwtUserIdExtractor.requireUserId(authentication)));
    }

    /**
     * 向当前用户拥有的会话发送消息，并返回角色回复。
     */
    @PostMapping("/sessions/{sessionId}/messages")
    public ApiResponse<SendChatMessageResponse> sendMessageWithCompleteReply(
            @PathVariable Long sessionId,
            @RequestBody SendChatMessageRequest request,
            Authentication authentication
    ) {
        Long userId = jwtUserIdExtractor.requireUserId(authentication);
        return ApiResponse.success(chatService.sendMessageWithCompleteReply(
                userId,
                sessionId,
                request.getContent()
        ));
    }

    /**
     * 向当前用户拥有的会话发送消息，并以 SSE 协议流式返回角色回复。
     *
     * <p>流式发送与完整回复发送复用同一套角色上下文、记忆和RAG构建逻辑。</p>
     */
    @PostMapping(value = "/sessions/{sessionId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessageWithStreamingReply(
            @PathVariable Long sessionId,
            @RequestBody SendChatMessageRequest request,
            Authentication authentication) {
        Long userId = jwtUserIdExtractor.requireUserId(authentication);
        return chatSseEmitterService.submit(session -> {
            chatService.sendMessageWithStreamingReply(
                    userId,
                    sessionId,
                    request.getContent(),
                    chunk -> session.send("delta", chunk)
            );
            session.send("complete", "done");
        });
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
