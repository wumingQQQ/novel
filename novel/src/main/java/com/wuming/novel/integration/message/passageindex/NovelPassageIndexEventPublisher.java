package com.wuming.novel.integration.message.passageindex;

import com.wuming.novel.integration.message.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "novel.mq.enabled", havingValue = "false", matchIfMissing = true)
public class NovelPassageIndexEventPublisher implements EventPublisher<NovelPassageIndexEvent> {
    /**
     * MQ关闭时仅记录Passage索引事件，避免本地和测试环境依赖RocketMQ。
     *
     * @param event Passage索引事件
     */
    @Override
    public void publish(NovelPassageIndexEvent event) {
        log.info("发送Passage向量索引事件，jobId: {}, novelId: {}, chapterId: {}, passageCount: {}",
                event.getJobId(), event.getNovelId(), event.getChapterId(), event.getPassageIds().size());
    }
}
