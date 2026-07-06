package com.wuming.novel.integration.message.chaptersplit;

import com.wuming.novel.integration.message.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "novel.mq.enabled", havingValue = "false", matchIfMissing = true)
public class ChapterAnalysisCompleteEventPublisher implements EventPublisher<ChapterAnalysisCompletedEvent> {
    @Override
    public void publish(ChapterAnalysisCompletedEvent event) {
        log.info("收到单章分析完成事件, jobId: {}, novelId: {}, chapterId: {}, chapterSequence: {}",
                event.getJobId(), event.getNovelId(), event.getChapterId(), event.getChapterSequence());
    }
}
