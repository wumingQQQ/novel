package com.wuming.novel.integration.message.listener;

import com.wuming.common.messaging.MqDestinations;
import com.wuming.novel.infrastructure.observability.TraceContext;
import com.wuming.novel.integration.message.rolereactionruleindex.RoleReactionRuleIndexEvent;
import com.wuming.novel.integration.rpc.rag.RoleReactionRuleVectorIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "novel.mq.enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = MqDestinations.NOVEL_EVENTS_TOPIC,
        selectorExpression = MqDestinations.ROLE_REACTION_RULE_INDEX_TAG,
        consumerGroup = "novel-role-reaction-rule-index-consumer-group"
)
public class RoleReactionRuleIndexEventListener implements RocketMQListener<RoleReactionRuleIndexEvent> {
    private final RoleReactionRuleVectorIndexService vectorIndexService;

    /**
     * 消费角色反应规则索引事件，异步删除旧向量并写入新向量。
     *
     * @param event 角色反应规则索引事件
     */
    @Override
    public void onMessage(RoleReactionRuleIndexEvent event) {
        try (TraceContext.MdcScope ignoredNovel = TraceContext.putNovelId(event.getNovelId())) {
            List<Long> deletedRuleIds = event.getDeletedRuleIds() == null ? List.of() : event.getDeletedRuleIds();
            List<Long> indexedRuleIds = event.getIndexedRuleIds() == null ? List.of() : event.getIndexedRuleIds();
            log.info("收到角色反应规则索引事件，novelId: {}, characterId: {}, characterName: {}, deleteCount: {}, indexCount: {}",
                    event.getNovelId(),
                    event.getCharacterId(),
                    event.getCharacterName(),
                    deletedRuleIds.size(),
                    indexedRuleIds.size());
            int deletedCount = vectorIndexService.deleteByIds(deletedRuleIds);
            boolean deleteDegraded = deletedCount < 0;
            if (deleteDegraded) {
                log.warn("角色反应规则旧向量删除降级，characterId: {}, requestCount: {}",
                        event.getCharacterId(), deletedRuleIds.size());
            }
            int indexedCount = vectorIndexService.indexByIds(indexedRuleIds);
            if (indexedCount < 0) {
                log.warn("角色反应规则向量索引降级，characterId: {}, requestCount: {}",
                        event.getCharacterId(), indexedRuleIds.size());
                return;
            }
            if (deleteDegraded) {
                log.warn("角色反应规则索引事件部分降级，characterId: {}, deletedCount: {}, indexedCount: {}",
                        event.getCharacterId(), deletedCount, indexedCount);
                return;
            }
            log.info("角色反应规则索引处理完成，characterId: {}, deletedCount: {}, indexedCount: {}",
                    event.getCharacterId(), deletedCount, indexedCount);
        }
    }
}
