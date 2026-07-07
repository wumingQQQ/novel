package com.wuming.novel.integration.message.listener;

import com.wuming.common.messaging.MqDestinations;
import com.wuming.novel.infrastructure.observability.TraceContext;
import com.wuming.novel.integration.message.passageindex.NovelPassageIndexEvent;
import com.wuming.novel.integration.rpc.rag.NovelPassageVectorIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "novel.mq.enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = MqDestinations.NOVEL_EVENTS_TOPIC,
        selectorExpression = MqDestinations.NOVEL_PASSAGE_INDEX_TAG,
        consumerGroup = "novel-passage-index-consumer-group"
)
public class NovelPassageIndexEventListener implements RocketMQListener<NovelPassageIndexEvent> {
    private final NovelPassageVectorIndexService vectorIndexService;

    /**
     * 消费Passage向量索引事件，异步写入RAG向量库。
     *
     * @param event Passage索引事件
     */
    @Override
    public void onMessage(NovelPassageIndexEvent event) {
        try (TraceContext.MdcScope ignoredJob = TraceContext.putJobId(event.getJobId());
             TraceContext.MdcScope ignoredNovel = TraceContext.putNovelId(event.getNovelId());
             TraceContext.MdcScope ignoredChapter = TraceContext.putChapterId(event.getChapterId())) {
            int passageCount = event.getPassageIds() == null ? 0 : event.getPassageIds().size();
            log.info("收到Passage向量索引事件，jobId: {}, novelId: {}, chapterId: {}, passageCount: {}",
                    event.getJobId(), event.getNovelId(), event.getChapterId(), passageCount);
            int indexedCount = vectorIndexService.indexByIds(event.getPassageIds());
            log.info("Passage向量索引完成，jobId: {}, novelId: {}, chapterId: {}, indexedCount: {}",
                    event.getJobId(), event.getNovelId(), event.getChapterId(), indexedCount);
        }
    }
}
