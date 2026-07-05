package com.wuming.rag.rerank;

/**
 * 排序完毕的文档
 * @param content
 * @param score
 */
public record RerankedDocument(
        String documentId,
        String content,
        double score
) {
}
