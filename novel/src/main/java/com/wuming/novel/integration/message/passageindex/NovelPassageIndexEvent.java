package com.wuming.novel.integration.message.passageindex;

import com.wuming.novel.integration.message.CompleteEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 小说Passage向量索引事件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NovelPassageIndexEvent extends CompleteEvent {
    /**
     * 已切分并落库的章节id。
     */
    private Long chapterId;

    /**
     * 待写入向量库的Passage主键列表。
     */
    private List<Long> passageIds = new ArrayList<>();

    /**
     * 待从向量库删除的旧Passage主键列表。
     */
    private List<Long> deletedPassageIds = new ArrayList<>();
}
