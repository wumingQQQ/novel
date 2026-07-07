package com.wuming.novel.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.novel.domain.entity.NovelPassage;
import com.wuming.novel.integration.rpc.rag.NovelPassageVectorIndexService;
import com.wuming.novel.service.INovelPassageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Passage向量索引补偿任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "novel.rag.passage-reindex.enabled", havingValue = "true", matchIfMissing = true)
public class NovelPassageReindexScheduler {
    private static final String VECTOR_PENDING = "PENDING";
    private static final String VECTOR_FAILED = "FAILED";

    private final INovelPassageService novelPassageService;
    private final NovelPassageVectorIndexService vectorIndexService;

    @Value("${novel.rag.passage-reindex.batch-size:${novel.rag.vector-batch-size:32}}")
    private int batchSize;

    /**
     * 定时补偿未完成的Passage向量索引。
     */
    @Scheduled(fixedDelayString = "${novel.rag.passage-reindex.fixed-delay-ms:300000}")
    public void reindex() {
        int limit = Math.max(1, batchSize);
        List<Long> passageIds = novelPassageService.list(new LambdaQueryWrapper<NovelPassage>()
                        .select(NovelPassage::getId)
                        .in(NovelPassage::getVectorStatus, VECTOR_PENDING, VECTOR_FAILED)
                        .orderByAsc(NovelPassage::getId)
                        .last("limit " + limit))
                .stream()
                .map(NovelPassage::getId)
                .toList();
        if (passageIds.isEmpty()) {
            return;
        }

        try {
            int indexedCount = vectorIndexService.indexByIds(passageIds);
            log.info("Passage向量索引补偿完成，requestCount: {}, indexedCount: {}",
                    passageIds.size(), indexedCount);
        } catch (RuntimeException e) {
            log.warn("Passage向量索引补偿失败，requestCount: {}", passageIds.size(), e);
        }
    }
}
