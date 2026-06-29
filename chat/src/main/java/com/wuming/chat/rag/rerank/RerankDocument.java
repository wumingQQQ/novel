package com.wuming.chat.rag.rerank;

/**
 *
 * 将文档转为待排序文档
 * @param chunkId
 * @param content
 */
public record RerankDocument(
        Long chunkId,
        String content
) {
}
