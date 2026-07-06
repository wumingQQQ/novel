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
     * 消费单章切分完成事件，后续按章节串行接入分析、Passage切分、人物识别和索引。
     *
     * @param event 章节切分完成事件
     */
    @Override
    public void onMessage(ChapterSplitCompletedEvent event) {
        try (TraceContext.MdcScope ignoredJob = TraceContext.putJobId(event.getJobId());
             TraceContext.MdcScope ignoredNovel = TraceContext.putNovelId(event.getNovelId());
             TraceContext.MdcScope ignoredChapter = TraceContext.putChapterId(event.getChapterId())) {
            log.info("收到单章切分完成事件，jobId: {}, novelId: {}, chapterId: {}, chapterSequence: {}",
                    event.getJobId(), event.getNovelId(), event.getChapterId(), event.getChapterSequence());
            startChapterRuntime(event);
        }
    }

    private void startChapterRuntime(ChapterSplitCompletedEvent event) {
        log.debug("单章运行链路待接入，jobId: {}, novelId: {}, chapterId: {}",
                event.getJobId(), event.getNovelId(), event.getChapterId());
    }
}
