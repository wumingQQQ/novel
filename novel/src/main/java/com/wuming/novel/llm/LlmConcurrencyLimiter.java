package com.wuming.novel.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * LLM 调用并发限流器。
 */
@Component
public class LlmConcurrencyLimiter {
    private final Semaphore semaphore;

    public LlmConcurrencyLimiter(@Value("${novel.llm.max-concurrency:4}") int maxConcurrency) {
        this.semaphore = new Semaphore(Math.max(1, maxConcurrency));
    }

    public <T> T execute(Supplier<T> supplier) {
        boolean acquired = false;
        try {
            semaphore.acquire();
            acquired = true;
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待LLM并发许可时被中断", e);
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }
}
