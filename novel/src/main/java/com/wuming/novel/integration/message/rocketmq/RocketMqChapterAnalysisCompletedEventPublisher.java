package com.wuming.novel.integration.message.rocketmq;

import com.wuming.common.messaging.MqDestinations;
import com.wuming.novel.infrastructure.observability.TraceContext;
import com.wuming.novel.integration.message.EventPublisher;
import com.wuming.novel.integration.message.chaptersplit.ChapterAnalysisCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "novel.mq.enabled", havingValue = "true")
public class RocketMqChapterAnalysisCompletedEventPublisher implements EventPublisher<ChapterAnalysisCompletedEvent> {
    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 发送单章切分完成事件，触发后续章节分析和Passage流水线。
     *
     * @param event 章节切分完成事件
     */
    @Override
    public void publish(ChapterAnalysisCompletedEvent event) {
        try (TraceContext.MdcScope ignoredJob = TraceContext.putJobId(event.getJobId());
             TraceContext.MdcScope ignoredNovel = TraceContext.putNovelId(event.getNovelId())) {
            log.info("开始发送单章切分完成事件，destination: {}, chapterId: {}, chapterSequence: {}",
                    MqDestinations.CHAPTER_ANALYSIS_COMPLETED,
                    event.getChapterId(),
                    event.getChapterSequence());
            rocketMQTemplate.convertAndSend(MqDestinations.CHAPTER_ANALYSIS_COMPLETED, event);
            log.info("单章切分完成事件发送成功，chapterId: {}, chapterSequence: {}",
                    event.getChapterId(),
                    event.getChapterSequence());
        }
    }
}
