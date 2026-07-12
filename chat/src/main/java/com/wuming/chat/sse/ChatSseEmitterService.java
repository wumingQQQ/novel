package com.wuming.chat.sse;

import com.wuming.chat.config.ChatSseProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 管理一次性聊天 SSE 连接，并在专用虚拟线程中执行回复任务。
 */
@Slf4j
@Service
public class ChatSseEmitterService {
    private final ExecutorService executor;
    private final Semaphore concurrentStreams;
    private final ChatSseProperties properties;

    /**
     * 创建服务专属的具名虚拟线程执行器，避免占用默认 ForkJoinPool。
     */
    public ChatSseEmitterService(ChatSseProperties properties) {
        this.executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("chat-sse-", 0).factory());
        this.properties = properties;
        this.concurrentStreams = new Semaphore(properties.concurrentLimit());
    }

    /** 在 Spring 容器关闭时等待或终止仍在运行的虚拟线程任务。 */
    @PreDestroy
    public void shutdown() {
        executor.close();
    }

    /**
     * 创建 SSE 连接并尝试投递聊天回复任务；达到并发上限时立即返回错误事件。
     *
     * @param task 在专用虚拟线程中执行的聊天回复任务
     * @return 已注册生命周期回调的 SSE emitter
     */
    public SseEmitter submit(Consumer<ChatSseSession> task) {
        ChatSseSession session = openSession();
        if (!concurrentStreams.tryAcquire()) {
            session.send("error", "当前聊天流请求过多，请稍后重试");
            session.complete();
            return session.emitter();
        }
        try {
            executor.execute(() -> runTask(session, task));
        } catch (RejectedExecutionException e) {
            concurrentStreams.release();
            log.warn("聊天SSE虚拟线程执行器拒绝任务", e);
            session.send("error", "聊天流任务暂时不可用，请稍后重试");
            session.complete();
        }
        return session.emitter();
    }

    /** 在虚拟线程中执行任务，并始终归还并发许可。 */
    private void runTask(ChatSseSession session, Consumer<ChatSseSession> task) {
        try {
            task.accept(session);
            session.complete();
        } catch (Exception e) {
            if (session.isClosed()) {
                log.debug("聊天SSE任务结束时连接已关闭，跳过错误事件发送");
                return;
            }
            log.warn("聊天SSE任务执行失败", e);
            session.send("error", "聊天回复生成失败，请稍后重试");
            session.complete();
        } finally {
            concurrentStreams.release();
        }
    }

    /** 创建连接并在完成、超时或客户端断连时禁止后续发送。 */
    private ChatSseSession openSession() {
        SseEmitter emitter = new SseEmitter(properties.timeoutMillis());
        ChatSseSession session = new ChatSseSession(emitter);
        emitter.onCompletion(session::markClosed);
        emitter.onTimeout(session::markClosed);
        emitter.onError(error -> session.markClosed());
        return session;
    }

    /**
     * 单次聊天流的发送与关闭句柄，不维护跨请求订阅状态。
     */
    public static class ChatSseSession {
        private final SseEmitter emitter;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private ChatSseSession(SseEmitter emitter) {
            this.emitter = emitter;
        }

        /** 发送一个命名 SSE 事件；客户端已断连时返回false。 */
        public boolean send(String eventName, Object data) {
            if (closed.get()) {
                return false;
            }
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
                return true;
            } catch (IOException | IllegalStateException e) {
                closed.set(true);
                log.debug("聊天SSE事件发送失败，eventName: {}, errorType: {}, errorMessage: {}",
                        eventName, e.getClass().getSimpleName(), e.getMessage());
                return false;
            }
        }

        /** 正常关闭连接，重复关闭会被忽略。 */
        public void complete() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                emitter.complete();
            } catch (IllegalStateException e) {
                log.debug("聊天SSE连接已关闭，跳过重复完成，errorMessage: {}", e.getMessage());
            }
        }

        /** 以异常关闭连接，供业务任务失败时通知客户端。 */
        public void fail(Throwable error) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                emitter.completeWithError(error);
            } catch (IllegalStateException e) {
                log.debug("聊天SSE连接已关闭，跳过重复异常完成，errorMessage: {}", e.getMessage());
            }
        }

        /** 标记连接已由容器或客户端关闭。 */
        private void markClosed() {
            closed.set(true);
        }

        /** 判断当前SSE连接是否已经关闭。 */
        public boolean isClosed() {
            return closed.get();
        }

        /** 返回当前会话持有的底层 emitter。 */
        private SseEmitter emitter() {
            return emitter;
        }
    }
}
