package com.wuming.rag.service;


import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.util.LinkedHashMap;
import java.util.Map;

public class EmbeddingStoreRegistry {
    private final Map<String, EmbeddingStore<TextSegment>> stores = new LinkedHashMap<>();

    public void register(String indexName, EmbeddingStore<TextSegment> store) {
        stores.put(indexName, store);
    }

    public EmbeddingStore<TextSegment> getRequired(String indexName) {
        EmbeddingStore<TextSegment> store = stores.get(indexName);
        if (store == null) {
            throw new IllegalArgumentException("未配置的RAG索引: " + indexName);
        }
        return store;
    }
}
