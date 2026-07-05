package com.wuming.rag.service;

import org.springframework.ai.vectorstore.VectorStore;

import java.util.LinkedHashMap;
import java.util.Map;

public class RagVectorStoreRegistry {
    private final Map<String, VectorStore> stores = new LinkedHashMap<>();

    public void registerVectorStore(String indexName, VectorStore vectorStore) {
        stores.put(indexName, vectorStore);
    }

    public VectorStore getRequired(String indexName) {
        VectorStore store = stores.get(indexName);
        if (store == null) {
            throw new IllegalArgumentException("未配置的RAG索引: " + indexName);
        }
        return store;
    }
}
