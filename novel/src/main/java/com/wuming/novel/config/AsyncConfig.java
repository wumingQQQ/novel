package com.wuming.novel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig {

    /**
     * 整体流程提交后的后台执行线程池，只负责承载 PipelineService 的同步流程。
     */
    @Bean("pipelineExecutor")
    public Executor pipelineExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("pipeline-executor-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }


    /**
     * LLM 调用以阻塞网络 IO 为主，使用虚拟线程承载大量等待中的请求。
     */
    @Bean(value = "llmExecutor", destroyMethod = "close")
    public ExecutorService llmExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                .name("llm-vt-", 0)
                .factory());
    }
}
