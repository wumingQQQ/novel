package com.wuming.novel.pipeline;

import com.wuming.novel.domain.enums.JobStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Pipeline阶段子项选择器。
 *
 * <p>选择规则固定为：优先重试失败项；没有失败项时，跳过已成功项。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StageItemSelector {
    private final RedisStageFailureStore redisStageFailureStore;

    public <T> List<T> selectLongBackedItems(Long jobId,
                                             JobStage stage,
                                             Function<List<Long>, List<T>> failedItemLoader,   // 获取id在前者中的失败项
                                             Supplier<List<T>> candidateLoader,   // 符合条件的所有项
                                             Function<T, Long> itemIdExtractor   // 获取目标项id的方法
    ) {
        List<Long> failedItemIds = redisStageFailureStore.consumeFailedLongItems(jobId, stage);
        // 失败项不为空，则可以直接返回失败项
        if (!failedItemIds.isEmpty()) {
            log.info("重试阶段失败子项，jobId: {}, stage: {}, failedCount: {}",
                    jobId, stage, failedItemIds.size());
            return failedItemLoader.apply(failedItemIds);
        }

        List<T> candidates = candidateLoader.get();
        List<Long> completedItemIds = redisStageFailureStore.completedLongItems(jobId, stage);
        // 完成项为空，说明还没有进行过处理
        if (completedItemIds.isEmpty()) {
            return candidates;
        }

        log.debug("跳过阶段已完成子项，jobId: {}, stage: {}, completedCount: {}",
                jobId, stage, completedItemIds.size());
        Set<Long> completedItemIdSet = new HashSet<>(completedItemIds);
        // 返回候选项中的失败项
        return candidates.stream()
                .filter(item -> !completedItemIdSet.contains(itemIdExtractor.apply(item)))
                .toList();
    }

    public List<String> selectStringItems(Long jobId, JobStage stage, Supplier<List<String>> candidateLoader) {
        List<String> failedItems = redisStageFailureStore.consumeFailedItems(jobId, stage);
        if (!failedItems.isEmpty()) {
            log.info("重试阶段失败子项，jobId: {}, stage: {}, failedCount: {}",
                    jobId, stage, failedItems.size());
            return failedItems;
        }

        List<String> candidates = candidateLoader.get();
        List<String> completedItems = redisStageFailureStore.completedItems(jobId, stage);
        if (completedItems.isEmpty()) {
            return candidates;
        }
        log.debug("跳过阶段已完成子项，jobId: {}, stage: {}, completedCount: {}",
                jobId, stage, completedItems.size());
        Set<String> completedItemSet = new HashSet<>(completedItems);
        return candidates.stream()
                .filter(item -> !completedItemSet.contains(item))
                .toList();
    }
}
