package com.wuming.chat.service.concurrency;

import com.wuming.chat.exception.ChatSessionBusyException;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 保证同一服务实例内每个聊天会话最多只有一个回复生成任务。
 */
@Component
public class ChatSessionExecutionGuard {
    private final ConcurrentMap<Long, Object> activeSessions = new ConcurrentHashMap<>();

    /** 获取会话执行权；会话忙时立即失败，不阻塞当前请求。 */
    public Lease acquire(Long sessionId) {
        Object token = new Object();

        if (activeSessions.putIfAbsent(sessionId, token) != null) {
            throw new ChatSessionBusyException();
        }

        // 按令牌删除，避免旧租约误释放后来请求持有的会话执行权。
        return () -> activeSessions.remove(sessionId, token);
    }

    /** 可由try-with-resources可靠释放的会话执行租约。 */
    @FunctionalInterface
    public interface Lease extends AutoCloseable {
        @Override
        void close();
    }
}
