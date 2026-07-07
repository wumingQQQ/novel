package com.wuming.novel.integration.message.roleexampleindex;

import com.wuming.novel.integration.message.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "novel.mq.enabled", havingValue = "false", matchIfMissing = true)
public class RoleExampleIndexEventPublisher implements EventPublisher<RoleExampleIndexEvent> {
    /**
     * MQ关闭时仅记录角色样本索引事件，避免本地和测试环境依赖RocketMQ。
     *
     * @param event 角色样本索引事件
     */
    @Override
    public void publish(RoleExampleIndexEvent event) {
        log.info("收到角色样本索引事件，novelId: {}, characterId: {}, characterName: {}, deleteCount: {}, indexCount: {}",
                event.getNovelId(),
                event.getCharacterId(),
                event.getCharacterName(),
                event.getDeletedExampleIds().size(),
                event.getIndexedExampleIds().size());
    }
}
