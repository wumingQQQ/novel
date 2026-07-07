package com.wuming.novel.integration.message.rolereactionruleindex;

import com.wuming.novel.integration.message.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "novel.mq.enabled", havingValue = "false", matchIfMissing = true)
public class RoleReactionRuleIndexEventPublisher implements EventPublisher<RoleReactionRuleIndexEvent> {
    /**
     * MQ关闭时仅记录角色反应规则索引事件。
     *
     * @param event 角色反应规则索引事件
     */
    @Override
    public void publish(RoleReactionRuleIndexEvent event) {
        log.info("收到角色反应规则索引事件，novelId: {}, characterId: {}, characterName: {}, deleteCount: {}, indexCount: {}",
                event.getNovelId(),
                event.getCharacterId(),
                event.getCharacterName(),
                event.getDeletedRuleIds().size(),
                event.getIndexedRuleIds().size());
    }
}
