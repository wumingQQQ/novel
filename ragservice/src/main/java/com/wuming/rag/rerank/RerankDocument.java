package com.wuming.rag.rerank;

/**
 * 将文档转为待排序文档
 * @param documentId  文档在向量库中的id
 * @param content
 */
public record RerankDocument(
        String documentId,
        String content
) {
}
