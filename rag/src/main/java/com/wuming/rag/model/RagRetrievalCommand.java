package com.wuming.rag.model;

import dev.langchain4j.store.embedding.filter.Filter;

public record RagRetrievalCommand(
        String indexName,
        String query,
        boolean multiQuery,
        Filter filter,
        int topK,
        int topN
) {
}
