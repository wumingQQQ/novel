package com.wuming.novel.integration.message.listener;

import com.wuming.common.messaging.MqDestinations;
import com.wuming.novel.infrastructure.observability.TraceContext;
import com.wuming.novel.integration.message.chaptersplit.ChapterSplitCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "novel.mq.enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = MqDestinations.NOVEL_EVENTS_TOPIC,
        selectorExpression = MqDestinations.CHAPTER_SPLIT_COMPLETED_TAG,
        consumerGroup = "novel-chapter-analysis-consumer-group"
)
public class ChapterSplitCompletedEventListener implements RocketMQListener<ChapterSplitCompletedEvent> {

    /**
     * 消费章节切分完成事件，后续在这里接入异步章节分析流程。
     *
     * @param event 章节切分完成事件
     */
    @Override
    public void onMessage(ChapterSplitCompletedEvent event) {
        try (TraceContext.MdcScope ignoredJob = TraceContext.putJobId(event.getJobId());
             TraceContext.MdcScope ignoredNovel = TraceContext.putNovelId(event.getNovelId())) {
            log.info("收到章节切分完成事件，jobId: {}, novelId: {}, chapterCount: {}",
                    event.getJobId(), event.getNovelId(), event.getChapterCount());
            startChapterAnalysis(event);
        }
    }

    private void startChapterAnalysis(ChapterSplitCompletedEvent event) {
        log.debug("章节分析流程待接入，jobId: {}, novelId: {}",
                event.getJobId(), event.getNovelId());
    }
}
