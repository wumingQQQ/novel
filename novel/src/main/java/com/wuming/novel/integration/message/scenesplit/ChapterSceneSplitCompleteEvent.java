package com.wuming.novel.integration.message.scenesplit;

import com.wuming.novel.integration.message.CompleteEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 某章节场景切分完毕的事件消息实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ChapterSceneSplitCompleteEvent extends CompleteEvent {
    private Long chapterId;
    private Integer chapterSequence;
    private Integer sceneCount;

    public ChapterSceneSplitCompleteEvent(){
        super();
    }
}
