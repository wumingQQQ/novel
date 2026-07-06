package com.wuming.novel.integration.message.chaptersplit;

import com.wuming.novel.integration.message.CompleteEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 小说章节切分完成事件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ChapterSplitCompletedEvent extends CompleteEvent {
    /**
     * 本次切分后的章节总数。
     */
    private Long chapterCount;
}
