package com.wuming.novel.integration.message.listener;

import com.wuming.common.messaging.MqDestinations;
import com.wuming.novel.infrastructure.observability.TraceContext;
import com.wuming.novel.integration.message.roleexampleindex.RoleExampleIndexEvent;
import com.wuming.novel.integration.rpc.rag.RoleExampleVectorIndexService;
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
        selectorExpression = MqDestinations.ROLE_EXAMPLE_INDEX_TAG,
        consumerGroup = "novel-role-example-index-consumer-group"
)
public class RoleExampleIndexEventListener implements RocketMQListener<RoleExampleIndexEvent> {
    private final RoleExampleVectorIndexService vectorIndexService;

    /**
     * 消费角色样本索引事件，异步删除旧向量并写入新向量。
     *
     * @param event 角色样本索引事件
     */
    @Override
    public void onMessage(RoleExampleIndexEvent event) {
        try (TraceContext.MdcScope ignoredNovel = TraceContext.putNovelId(event.getNovelId())) {
            log.info("收到角色样本索引事件，novelId: {}, characterId: {}, characterName: {}, deleteCount: {}, indexCount: {}",
                    event.getNovelId(),
                    event.getCharacterId(),
                    event.getCharacterName(),
                    event.getDeletedExampleIds().size(),
                    event.getIndexedExampleIds().size());
            int deletedCount = vectorIndexService.deleteByIds(event.getDeletedExampleIds());
            int indexedCount = vectorIndexService.indexByIds(event.getIndexedExampleIds());
            log.info("角色样本索引处理完成，characterId: {}, deletedCount: {}, indexedCount: {}",
                    event.getCharacterId(), deletedCount, indexedCount);
        }
    }
}
