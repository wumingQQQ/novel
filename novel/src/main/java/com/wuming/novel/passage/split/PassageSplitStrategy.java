package com.wuming.novel.passage.split;

import java.util.List;

/**
 * Passage切分策略。
 */
public interface PassageSplitStrategy {

    /**
     * 当前策略类型。
     */
    PassageSplitStrategyType type();

    /**
     * 将章节正文切分为Passage片段。
     *
     * @param content 章节正文
     * @return 切分结果
     */
    List<PassageSlice> split(String content);
}
