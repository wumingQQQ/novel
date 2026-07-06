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
        log.info("单章切分完成，jobId: {}, novelId: {}, chapterId: {}, chapterSequence: {}",
                event.getJobId(), event.getNovelId(), event.getChapterId(), event.getChapterSequence());
    }
}
