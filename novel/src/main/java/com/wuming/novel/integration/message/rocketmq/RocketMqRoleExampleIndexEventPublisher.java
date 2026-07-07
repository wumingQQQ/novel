package com.wuming.novel.integration.message.rocketmq;

import com.wuming.common.messaging.MqDestinations;
import com.wuming.novel.infrastructure.observability.TraceContext;
import com.wuming.novel.integration.message.EventPublisher;
import com.wuming.novel.integration.message.roleexampleindex.RoleExampleIndexEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "novel.mq.enabled", havingValue = "true")
public class RocketMqRoleExampleIndexEventPublisher implements EventPublisher<RoleExampleIndexEvent> {
    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 发送角色样本索引事件，触发异步写入RAG向量库。
     *
     * @param event 角色样本索引事件
     */
    @Override
    public void publish(RoleExampleIndexEvent event) {
        try (TraceContext.MdcScope ignoredNovel = TraceContext.putNovelId(event.getNovelId())) {
            log.info("开始发送角色样本索引事件，destination: {}, characterId: {}, deleteCount: {}, indexCount: {}",
                    MqDestinations.ROLE_EXAMPLE_INDEX,
                    event.getCharacterId(),
                    event.getDeletedExampleIds().size(),
                    event.getIndexedExampleIds().size());
            rocketMQTemplate.convertAndSend(MqDestinations.ROLE_EXAMPLE_INDEX, event);
            log.info("角色样本索引事件发送成功，characterId: {}, characterName: {}",
                    event.getCharacterId(), event.getCharacterName());
        }
    }
}
