package com.wuming.chat.rag.rerank;

/**
 * 排序完毕的文档
 * @param chunkId
 * @param content
 * @param score
 */
public record RerankedDocument(
        Long chunkId,
        String content,
        double score
) {
}
