package com.wuming.novel.passage.split;

/**
 * Passage切分结果。
 *
 * @param sequence 章节内切分顺序，从1开始
 * @param start 起始位置，段落策略为段落编号，字符策略为字符位置
 * @param end 结束位置，段落策略为段落编号，字符策略为字符位置
 * @param content 切分后的正文
 */
public record PassageSlice(int sequence, int start, int end, String content) {
}
