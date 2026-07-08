package com.wuming.novel.integration.message.rocketmq;

import com.wuming.common.messaging.MqDestinations;
import com.wuming.novel.infrastructure.observability.TraceContext;
import com.wuming.novel.integration.message.EventPublisher;
import com.wuming.novel.integration.message.passageindex.NovelPassageIndexEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "novel.mq.enabled", havingValue = "true")
public class RocketMqNovelPassageIndexEventPublisher implements EventPublisher<NovelPassageIndexEvent> {
    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 发送Passage向量索引事件，触发异步写入RAG向量库。
     *
     * @param event Passage索引事件
     */
    @Override
    public void publish(NovelPassageIndexEvent event) {
        try (TraceContext.MdcScope ignoredJob = TraceContext.putJobId(event.getJobId());
             TraceContext.MdcScope ignoredNovel = TraceContext.putNovelId(event.getNovelId());
             TraceContext.MdcScope ignoredChapter = TraceContext.putChapterId(event.getChapterId())) {
            int deleteCount = event.getDeletedPassageIds() == null ? 0 : event.getDeletedPassageIds().size();
            int passageCount = event.getPassageIds() == null ? 0 : event.getPassageIds().size();
            log.info("开始发送Passage向量索引事件，destination: {}, deleteCount: {}, passageCount: {}",
                    MqDestinations.NOVEL_PASSAGE_INDEX,
                    deleteCount,
                    passageCount);
            rocketMQTemplate.convertAndSend(MqDestinations.NOVEL_PASSAGE_INDEX, event);
            log.info("Passage向量索引事件发送成功，chapterId: {}, deleteCount: {}, passageCount: {}",
                    event.getChapterId(),
                    deleteCount,
                    passageCount);
        }
    }
}
