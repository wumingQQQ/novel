package com.wuming.rag.model;

import dev.langchain4j.store.embedding.filter.Filter;

import java.util.List;

public record RagRetrievalCommand(
        String indexName,
        String query,
        List<String> queries,
        Filter filter,
        int topK,
        int topN
) {
}
