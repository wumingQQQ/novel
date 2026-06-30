package com.wuming.chat.integration.message.listener;

import com.wuming.chat.integration.message.dto.ChapterSceneSplitCompleteMessage;
import com.wuming.chat.infrastructure.observability.TraceContext;
import com.wuming.chat.rag.index.SceneRagIndexService;
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

    /**
     * 消费章节场景切分完成消息，并触发该章节场景向量索引。
     *
     * @param message 章节场景切分完成消息
     */
    @Override
    public void onMessage(ChapterSceneSplitCompleteMessage message) {
        try (TraceContext.MdcScope ignoredJob = TraceContext.putJobId(message.getJobId())) {
            log.info("收到章节切分完成消息，chapterId: {}", message.getChapterId());
            sceneRagIndexService.indexChapterScenes(message);
        }
    }
}

