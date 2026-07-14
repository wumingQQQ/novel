package com.wuming.novel.passage.split;

/**
 * Passage切分策略类型。
 */
public enum PassageSplitStrategyType {
    /**
     * 根据小说抽样章节自动选择整本书的切分策略。
     */
    AUTO,

    /**
     * 按段落滑动窗口切分，start/end表示段落编号。
     */
    PARAGRAPH_WINDOW,

    /**
     * 按字符长度重叠切分，start/end表示字符位置。
     */
    CHARACTER_OVERLAP
}
