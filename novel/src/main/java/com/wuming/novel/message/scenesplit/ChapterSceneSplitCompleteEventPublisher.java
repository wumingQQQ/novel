package com.wuming.novel.message.scenesplit;

import com.wuming.novel.message.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@ConditionalOnProperty(name = "novel.mq.enabled", havingValue = "false", matchIfMissing = true)
public class ChapterSceneSplitCompleteEventPublisher implements EventPublisher<ChapterSceneSplitCompleteEvent> {
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
    }
}
