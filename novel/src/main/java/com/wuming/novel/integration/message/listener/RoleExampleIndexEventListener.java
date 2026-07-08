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

import java.util.List;

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
            List<Long> deletedExampleIds = event.getDeletedExampleIds() == null ? List.of() : event.getDeletedExampleIds();
            List<Long> indexedExampleIds = event.getIndexedExampleIds() == null ? List.of() : event.getIndexedExampleIds();
            log.info("收到角色样本索引事件，novelId: {}, characterId: {}, characterName: {}, deleteCount: {}, indexCount: {}",
                    event.getNovelId(),
                    event.getCharacterId(),
                    event.getCharacterName(),
                    deletedExampleIds.size(),
                    indexedExampleIds.size());
            int deletedCount = vectorIndexService.deleteByIds(deletedExampleIds);
            boolean deleteDegraded = deletedCount < 0;
            if (deleteDegraded) {
                log.warn("角色样本旧向量删除降级，characterId: {}, requestCount: {}",
                        event.getCharacterId(), deletedExampleIds.size());
            }
            int indexedCount = vectorIndexService.indexByIds(indexedExampleIds);
            if (indexedCount < 0) {
                log.warn("角色样本向量索引降级，characterId: {}, requestCount: {}",
                        event.getCharacterId(), indexedExampleIds.size());
                return;
            }
            if (deleteDegraded) {
                log.warn("角色样本索引事件部分降级，characterId: {}, deletedCount: {}, indexedCount: {}",
                        event.getCharacterId(), deletedCount, indexedCount);
                return;
            }
            log.info("角色样本索引处理完成，characterId: {}, deletedCount: {}, indexedCount: {}",
                    event.getCharacterId(), deletedCount, indexedCount);
        }
    }
}
