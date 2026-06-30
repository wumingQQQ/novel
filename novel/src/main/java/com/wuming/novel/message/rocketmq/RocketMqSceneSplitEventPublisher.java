package com.wuming.novel.message.rocketmq;

import com.wuming.novel.message.EventPublisher;
import com.wuming.novel.message.scenesplit.ChapterSceneSplitCompleteEvent;
import com.wuming.novel.infrastructure.observability.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "novel.mq.enabled", havingValue = "true")
public class RocketMqSceneSplitEventPublisher implements EventPublisher<ChapterSceneSplitCompleteEvent> {
    private final RocketMQTemplate rocketMQTemplate;
    private final RocketMqTagProperties tagProperties;

    @Value("${novel.mq.topic}")
    private String topic;

    /**
     * 发送单章节场景切分完成事件，供chat模块拉取场景并建立RAG索引。
     *
     * @param event 单章节场景切分完成事件
     */
    @Override
    public void publish(ChapterSceneSplitCompleteEvent event) {
        try (TraceContext.MdcScope ignoredJob = TraceContext.putJobId(event.getJobId());
             TraceContext.MdcScope ignoredNovel = TraceContext.putNovelId(event.getNovelId());
             TraceContext.MdcScope ignoredChapter = TraceContext.putChapterId(event.getChapterId())) {
            String destination = topic + ":" + tagProperties.getSingleChapterSplitCompleted();
            log.info("开始发送章节切分完成事件，destination: {}, chapterSeq: {}, sceneCount: {}",
                    destination, event.getChapterSequence(), event.getSceneCount());
            rocketMQTemplate.convertAndSend(destination, event);
            log.info("章节切分完成事件发送成功，destination: {}, chapterSeq: {}, sceneCount: {}",
                    destination, event.getChapterSequence(), event.getSceneCount());
        }
    }
}
