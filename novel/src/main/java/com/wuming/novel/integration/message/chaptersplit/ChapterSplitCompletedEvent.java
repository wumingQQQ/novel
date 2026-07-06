package com.wuming.novel.integration.message.chaptersplit;

import com.wuming.novel.integration.message.CompleteEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 单章切分完成事件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ChapterSplitCompletedEvent extends CompleteEvent {
    /**
     * 已切分并落库的章节id。
     */
    private Long chapterId;

    /**
     * 章节在小说中的顺序。
     */
    private Integer chapterSequence;

    /**
     * 章节标题。
     */
    private String chapterTitle;
}
