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

import java.util.List;

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
            List<Long> deletedPassageIds = event.getDeletedPassageIds() == null ? List.of() : event.getDeletedPassageIds();
            List<Long> passageIds = event.getPassageIds() == null ? List.of() : event.getPassageIds();
            log.info("收到Passage向量索引事件，jobId: {}, novelId: {}, chapterId: {}, deleteCount: {}, passageCount: {}",
                    event.getJobId(), event.getNovelId(), event.getChapterId(), deletedPassageIds.size(), passageIds.size());
            int deletedCount = vectorIndexService.deleteByIds(deletedPassageIds);
            boolean deleteDegraded = deletedCount < 0;
            if (deleteDegraded) {
                log.warn("Passage旧向量删除降级，jobId: {}, novelId: {}, chapterId: {}, requestCount: {}",
                        event.getJobId(), event.getNovelId(), event.getChapterId(), deletedPassageIds.size());
            }
            int indexedCount = vectorIndexService.indexByIds(passageIds);
            if (indexedCount < 0) {
                log.warn("Passage向量索引降级，jobId: {}, novelId: {}, chapterId: {}, requestCount: {}",
                        event.getJobId(), event.getNovelId(), event.getChapterId(), passageIds.size());
                return;
            }
            if (deleteDegraded) {
                log.warn("Passage向量索引事件部分降级，jobId: {}, novelId: {}, chapterId: {}, deletedCount: {}, indexedCount: {}",
                        event.getJobId(), event.getNovelId(), event.getChapterId(), deletedCount, indexedCount);
                return;
            }
            log.info("Passage向量索引完成，jobId: {}, novelId: {}, chapterId: {}, indexedCount: {}",
                    event.getJobId(), event.getNovelId(), event.getChapterId(), indexedCount);
        }
    }
}
