package com.wuming.novel.message.rocketmq;

import com.wuming.novel.message.EventPublisher;
import com.wuming.novel.message.scenesplit.ChapterSceneSplitCompleteEvent;
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

    @Override
    public void publish(ChapterSceneSplitCompleteEvent event) {
        log.info(
                "章节场景切分完成事件已生成，jobId: {}, novelId: {}, chapterId: {}, chapterSeq: {}, sceneCount: {}",
                event.getJobId(),
                event.getNovelId(),
                event.getChapterId(),
                event.getChapterSequence(),
                event.getSceneCount()
        );
        String destination = topic + ":" + tagProperties.getSingleChapterSplitCompleted();
        rocketMQTemplate.convertAndSend(
                destination,
                event
        );
    }
}
