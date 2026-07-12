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
     * 以 SSE 协议发送角色回复。
     *
     * <p>当前先复用同步发送链路，确保个人角色版本、记忆和RAG上下文与普通发送保持一致；
     * 后续切换 token 流时也应复用同一套上下文构建逻辑。</p>
     */
    @PostMapping(value = "/sessions/{sessionId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(
            @PathVariable Long sessionId,
            @RequestBody SendChatMessageRequest request,
            Authentication authentication) {
        Long userId = jwtUserIdExtractor.requireUserId(authentication);
        return chatSseEmitterService.submit(session -> {
            SendChatMessageResponse response = chatService.sendMessage(userId, sessionId, request.getContent());
            if (session.send("message", response)) {
                session.send("complete", "done");
            }
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
