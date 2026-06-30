package com.wuming.chat.rag.retrieve;

/**
 * 对最终注入聊天提示词的原文片段的包装，便于调试
 * @param documentId
 * @param sceneId
 * @param chapterSequence
 * @param sceneSequence
 * @param content
 * @param score
 */
public record RagContext(
        String documentId,
        Long sceneId,
        Integer chapterSequence,
        Integer sceneSequence,
        String content,
        double score
) {
}
