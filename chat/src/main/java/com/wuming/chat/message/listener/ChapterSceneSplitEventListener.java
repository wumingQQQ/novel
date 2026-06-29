package com.wuming.chat.message.listener;

import com.wuming.chat.message.eventdto.ChapterSceneSplitCompleteMessage;
import com.wuming.chat.rag.SceneRagIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = "${chat.mq.topic}",
        selectorExpression = "${chat.mq.tags.single-chapter-split-complete}",
        consumerGroup = "chat-scene-consumer-group"
)
@RequiredArgsConstructor
public class ChapterSceneSplitEventListener implements RocketMQListener<ChapterSceneSplitCompleteMessage> {
    private final SceneRagIndexService sceneRagIndexService;
    @Override
    public void onMessage(ChapterSceneSplitCompleteMessage message) {
        log.info("message={}", message);
        sceneRagIndexService.indexChapterScenes(message);
    }
}
