package com.wuming.novel.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.novel.domain.entity.RoleReactionRule;
import com.wuming.novel.integration.rpc.rag.RoleReactionRuleVectorIndexService;
import com.wuming.novel.service.IRoleReactionRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 角色反应规则向量索引补偿任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "novel.rag.reaction-rule-reindex.enabled", havingValue = "true", matchIfMissing = true)
public class RoleReactionRuleReindexScheduler {
    private static final String VECTOR_PENDING = "PENDING";
    private static final String VECTOR_FAILED = "FAILED";

    private final IRoleReactionRuleService roleReactionRuleService;
    private final RoleReactionRuleVectorIndexService vectorIndexService;

    @Value("${novel.rag.reaction-rule-reindex.batch-size:${novel.rag.vector-batch-size:32}}")
    private int batchSize;

    /**
     * 定时补偿未完成的角色反应规则向量索引。
     */
    @Scheduled(fixedDelayString = "${novel.rag.reaction-rule-reindex.fixed-delay-ms:300000}")
    public void reindex() {
        int limit = Math.max(1, batchSize);
        List<Long> ruleIds = roleReactionRuleService.list(new LambdaQueryWrapper<RoleReactionRule>()
                        .select(RoleReactionRule::getId)
                        .in(RoleReactionRule::getVectorStatus, VECTOR_PENDING, VECTOR_FAILED)
                        .orderByAsc(RoleReactionRule::getId)
                        .last("limit " + limit))
                .stream()
                .map(RoleReactionRule::getId)
                .toList();
        if (ruleIds.isEmpty()) {
            return;
        }

        try {
            int indexedCount = vectorIndexService.indexByIds(ruleIds);
            log.info("角色反应规则向量索引补偿完成，requestCount: {}, indexedCount: {}",
                    ruleIds.size(), indexedCount);
        } catch (RuntimeException e) {
            log.warn("角色反应规则向量索引补偿失败，requestCount: {}, errorType: {}, errorMessage: {}",
                    ruleIds.size(), e.getClass().getSimpleName(), e.getMessage());
            log.debug("角色反应规则向量索引补偿失败堆栈，requestCount: {}", ruleIds.size(), e);
        }
    }
}
