package com.wuming.chat.infrastructure.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuming.chat.domain.entity.ChatMessage;
import com.wuming.common.cache.redis.RedisKey;
import com.wuming.common.cache.redis.RedisListOps;
import com.wuming.chat.domain.model.ChatHistoryMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageCacheService {
    private static final String KEY_PREFIX = "chat:messages";

    private final RedisListOps redisListOps;
    private final ObjectMapper objectMapper;

    @Value("${chat.memory.recent-message-limit:16}")
    private int recentMessageLimit;

    @Value("${chat.message-cache-ttl:3d}")
    private Duration messageCacheTtl;

    /**
     * 将新消息追加到 Redis 最近消息列表，并裁剪到上下文窗口大小。
     */
    public void append(ChatMessage message) {
        try {
            String key = key(message.getSessionId());
            String value = objectMapper.writeValueAsString(
                    new ChatHistoryMessage(message.getRole(), message.getContent())
            );
            redisListOps.rightPush(key, value);
            redisListOps.trim(key, -limit(), -1);
            redisListOps.expire(key, messageCacheTtl);
        } catch (Exception e) {
            log.warn("会话{}消息写入Redis缓存失败", message.getSessionId(), e);
        }
    }

    /**
     * 从 Redis 读取最近消息；缓存缺失或解析失败时返回空列表。
     */
    public List<ChatHistoryMessage> recentMessages(Long sessionId) {
        try {
            List<String> values = redisListOps.range(key(sessionId), 0, -1);
            if (values == null || values.isEmpty()) {
                return Collections.emptyList();
            }
            return values.stream()
                    .map(this::readMessage)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.warn("会话{}消息读取Redis缓存失败", sessionId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 使用数据库最近消息重建 Redis 缓存。
     */
    public void refresh(Long sessionId, List<ChatMessage> messages) {
        try {
            String key = key(sessionId);
            redisListOps.delete(key);
            for (ChatMessage message : messages) {
                String value = objectMapper.writeValueAsString(
                        new ChatHistoryMessage(message.getRole(), message.getContent())
                );
                redisListOps.rightPush(key, value);
            }
            redisListOps.trim(key, -limit(), -1);
            redisListOps.expire(key, messageCacheTtl);
        } catch (Exception e) {
            log.warn("会话{}消息刷新Redis缓存失败", sessionId, e);
        }
    }

    private ChatHistoryMessage readMessage(String value) {
        try {
            return objectMapper.readValue(value, ChatHistoryMessage.class);
        } catch (JsonProcessingException e) {
            log.warn("Redis聊天消息解析失败", e);
            return null;
        }
    }

    private String key(Long sessionId) {
        return RedisKey.join(KEY_PREFIX, String.valueOf(sessionId));
    }

    private int limit() {
        return Math.max(1, recentMessageLimit);
    }
}

