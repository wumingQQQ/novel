package com.wuming.novel.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.novel.domain.entity.RoleExample;
import com.wuming.novel.integration.rpc.rag.RoleExampleVectorIndexService;
import com.wuming.novel.service.IRoleExampleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 角色样本向量索引补偿任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "novel.rag.role-example-reindex.enabled", havingValue = "true", matchIfMissing = true)
public class RoleExampleReindexScheduler {
    private static final String VECTOR_PENDING = "PENDING";
    private static final String VECTOR_FAILED = "FAILED";

    private final IRoleExampleService roleExampleService;
    private final RoleExampleVectorIndexService vectorIndexService;

    @Value("${novel.rag.role-example-reindex.batch-size:${novel.rag.vector-batch-size:32}}")
    private int batchSize;

    /**
     * 定时补偿未完成的角色样本向量索引。
     */
    @Scheduled(fixedDelayString = "${novel.rag.role-example-reindex.fixed-delay-ms:300000}")
    public void reindex() {
        int limit = Math.max(1, batchSize);
        List<Long> exampleIds = roleExampleService.list(new LambdaQueryWrapper<RoleExample>()
                        .select(RoleExample::getId)
                        .in(RoleExample::getVectorStatus, VECTOR_PENDING, VECTOR_FAILED)
                        .orderByAsc(RoleExample::getId)
                        .last("limit " + limit))
                .stream()
                .map(RoleExample::getId)
                .toList();
        if (exampleIds.isEmpty()) {
            return;
        }

        try {
            int indexedCount = vectorIndexService.indexByIds(exampleIds);
            log.info("角色样本向量索引补偿完成，requestCount: {}, indexedCount: {}",
                    exampleIds.size(), indexedCount);
        } catch (RuntimeException e) {
            log.warn("角色样本向量索引补偿失败，requestCount: {}, errorType: {}, errorMessage: {}",
                    exampleIds.size(), e.getClass().getSimpleName(), e.getMessage());
            log.debug("角色样本向量索引补偿失败堆栈，requestCount: {}", exampleIds.size(), e);
        }
    }
}
