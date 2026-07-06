package com.wuming.novel.integration.message.chaptersplit;

import com.wuming.novel.integration.message.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "novel.mq.enabled", havingValue = "false", matchIfMissing = true)
public class ChapterSplitCompletedEventPublisher implements EventPublisher<ChapterSplitCompletedEvent> {
    @Override
    public void publish(ChapterSplitCompletedEvent event) {
        log.info("章节切分完成，jobId: {}, novelId: {}, chapterCount: {}",
                event.getJobId(), event.getNovelId(), event.getChapterCount());
    }
}
