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
            log.info("收到角色反应规则索引事件，novelId: {}, characterId: {}, characterName: {}, deleteCount: {}, indexCount: {}",
                    event.getNovelId(),
                    event.getCharacterId(),
                    event.getCharacterName(),
                    event.getDeletedRuleIds().size(),
                    event.getIndexedRuleIds().size());
            int deletedCount = vectorIndexService.deleteByIds(event.getDeletedRuleIds());
            int indexedCount = vectorIndexService.indexByIds(event.getIndexedRuleIds());
            log.info("角色反应规则索引处理完成，characterId: {}, deletedCount: {}, indexedCount: {}",
                    event.getCharacterId(), deletedCount, indexedCount);
        }
    }
}
