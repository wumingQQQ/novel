package com.wuming.rag.rerank;

public record RerankedDocument(
        String documentId,
        String content,
        double score
) {
}
