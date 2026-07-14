package com.wuming.novel.pipeline;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Pipeline阶段子项并发执行器。
 *
 * <p>只统一CompletableFuture编排；成功、失败记录和业务汇总仍由各Step决定。</p>
 */
@Component
public class AsyncStageItemRunner {

    public <I, O> List<O> run(List<I> items,    // 待处理的子项
                              Executor executor,         // 处理任务的线程池
                              Consumer<I> worker,        // 处理单项任务的具体逻辑
                              StageItemRunHandler<I, O> handler  // 单项任务处理完毕的收尾工作，记录进度与日志等
    ) {
        return items.stream()
                .map(item -> CompletableFuture
                        .runAsync(() -> worker.accept(item), executor)
                        .handle((ignored, throwable) -> handler.handle(item, throwable)))
                .toList()
                .stream()
                .map(CompletableFuture::join)
                .toList();
    }

    public <I, R, O> List<O> supply(List<I> items,
                                    Executor executor,
                                    Function<I, R> worker,
                                    StageItemSupplyHandler<I, R, O> handler
    ) {
        return items.stream()
                .map(item -> CompletableFuture
                        .supplyAsync(() -> worker.apply(item), executor)
                        .handle((result, throwable) -> handler.handle(item, result, throwable)))
                .toList()
                .stream()
                .map(CompletableFuture::join)
                .toList();
    }

    @FunctionalInterface
    public interface StageItemRunHandler<I, O> {
        O handle(I item, Throwable throwable);
    }

    @FunctionalInterface
    public interface StageItemSupplyHandler<I, R, O> {
        O handle(I item, R result, Throwable throwable);
    }
}
