package com.wuming.rag.service;

import com.wuming.api.rag.RagFacade;
import com.wuming.api.rag.dto.*;
import com.wuming.api.rag.dto.spec.SearchFilter;
import com.wuming.api.rag.dto.spec.SearchRequest;
import com.wuming.rag.config.RagProperties;
import com.wuming.rag.model.RagRetrievalCommand;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.ContainsString;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import dev.langchain4j.store.embedding.filter.logical.And;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.json.Path2;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;


@Slf4j
@DubboService
@RequiredArgsConstructor
public class RagService implements RagFacade {
    private static final Pattern METADATA_FIELD_PATTERN = Pattern.compile("[A-Za-z0-9_]+");

    private final EmbeddingStoreRegistry registry;
    private final EmbeddingModel embeddingModel;
    private final RagRetrieveService retrieveService;
    private final RagProperties ragProperties;
    private final UnifiedJedis unifiedJedis;

    @Override
    public int upsertDocuments(UpsertDocumentRequest request) {
        if (request.getDocuments() == null || request.getDocuments().isEmpty()) {
            log.debug("跳过RAG文档写入，文档列表为空，indexName: {}", request.getIndexName());
            return 0;
        }
        EmbeddingStore<TextSegment> store = registry.getRequired(request.getIndexName());

        List<RagDocument> documents = request.getDocuments().stream()
                .filter(Objects::nonNull)
                .filter(doc -> hasText(doc.getDocumentId()) && hasText(doc.getContent()))
                .toList();
        if (documents.isEmpty()) {
            log.debug("跳过RAG文档写入，过滤后无有效文档，indexName: {}, originalCount: {}",
                    request.getIndexName(), request.getDocuments().size());
            return 0;
        }
        List<TextSegment> segments = documents.stream()
                .map(this::toTextSegment)
                .toList();
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        store.addAll(
                documents.stream().map(RagDocument::getDocumentId).toList(),
                embeddings,
                segments
        );

        log.info("RAG文档写入完成，indexName: {}, documentCount: {}", request.getIndexName(), documents.size());
        return documents.size();
    }

    private TextSegment toTextSegment(RagDocument document) {
        Map<String, Object> metadata = document.getMetadata() == null
                ? new LinkedHashMap<>()
                :  new LinkedHashMap<>(document.getMetadata());

        return TextSegment.from(document.getContent(), Metadata.from(metadata));
    }

    @Override
    public int deleteDocuments(DeleteDocumentRequest request) {
        if (request.getDocumentIds() == null || request.getDocumentIds().isEmpty()) {
            log.debug("跳过RAG文档删除，文档ID列表为空，indexName: {}", request.getIndexName());
            return 0;
        }
        EmbeddingStore<TextSegment> store = registry.getRequired(request.getIndexName());
        store.removeAll(request.getDocumentIds());
        log.info("RAG文档删除完成，indexName: {}, documentCount: {}",
                request.getIndexName(), request.getDocumentIds().size());
        return request.getDocumentIds().size();
    }

    @Override
    public int updateDocumentMetadata(UpdateDocumentMetadataRequest request) {
        if (request == null || request.getPatches() == null || request.getPatches().isEmpty()) {
            return 0;
        }
        RagProperties.Index index = ragProperties.getIndexes().get(request.getIndexName());
        if (index == null) {
            throw new IllegalArgumentException("未配置的RAG索引: " + request.getIndexName());
        }

        int updatedCount = 0;
        for (UpdateDocumentMetadataRequest.DocumentMetadataPatch patch : request.getPatches()) {
            if (patch == null || !hasText(patch.getDocumentId())
                    || patch.getMetadata() == null || patch.getMetadata().isEmpty()) {
                continue;
            }
            String key = index.getKeyPrefix() + patch.getDocumentId();
            boolean updated = false;
            for (Map.Entry<String, Object> entry : patch.getMetadata().entrySet()) {
                validateMetadataField(entry.getKey());
                String result = unifiedJedis.jsonSetWithEscape(
                        key,
                        Path2.of("$." + entry.getKey()),
                        entry.getValue()
                );
                updated = updated || "OK".equals(result);
            }
            if (updated) {
                updatedCount++;
            }
        }

        log.info("RAG文档元数据更新完成，indexName: {}, requestCount: {}, updatedCount: {}",
                request.getIndexName(), request.getPatches().size(), updatedCount);
        return updatedCount;
    }

    @Override
    public List<SearchHit> search(SearchRequest request) {
        validateSearchRequest(request);

        String indexName = request.getIndexName().trim();
        List<String> searchQueries = searchQueries(request.getQuery(), request.getQueries());
        if (searchQueries.isEmpty()) {
            throw new IllegalArgumentException("RAG检索query和queries不能同时为空");
        }
        String retrievalQuery = hasText(request.getQuery()) ? request.getQuery().trim() : searchQueries.getFirst();
        Filter filter = toFilter(indexName, request.getFilters());

        List<SearchHit> hits = retrieveService.retrieve(new RagRetrievalCommand(
                indexName,
                retrievalQuery,
                searchQueries,
                filter,
                normalizeTopK(request.getTopK()),
                normalizeTopN(request.getTopN(), request.getTopK())
        ));
        log.debug("RAG通用召回完成，indexName: {}, queryCount: {}, filterCount: {}, topK: {}, topN: {}, hitCount: {}",
                indexName, searchQueries.size(),
                request.getFilters() == null ? 0 : request.getFilters().size(),
                normalizeTopK(request.getTopK()), normalizeTopN(request.getTopN(), request.getTopK()), hits.size());
        return hits;
    }

    private void validateSearchRequest(SearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("RAG检索请求不能为空");
        }
        if (!hasText(request.getIndexName())) {
            throw new IllegalArgumentException("RAG索引名称不能为空");
        }
        if (!ragProperties.getIndexes().containsKey(request.getIndexName().trim())) {
            throw new IllegalArgumentException("未配置的RAG索引: " + request.getIndexName());
        }
        if (!hasText(request.getQuery()) && (request.getQueries() == null || request.getQueries().isEmpty())) {
            throw new IllegalArgumentException("RAG检索query和queries不能同时为空");
        }
    }

    private int normalizeTopK(Integer topK) {
        return topK == null || topK <= 0 ? 20 : topK;
    }

    private int normalizeTopN(Integer topN, Integer topK) {
        int normalizedTopK = normalizeTopK(topK);
        return topN == null || topN <= 0 ? normalizedTopK : topN;
    }

    private Filter toFilter(String indexName, List<SearchFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }

        List<Filter> converted = filters.stream()
                .filter(Objects::nonNull)
                .map(filter -> toLangChainFilter(indexName, filter))
                .toList();
        if (converted.isEmpty()) {
            return null;
        }
        if (converted.size() == 1) {
            return converted.getFirst();
        }

        Filter result = converted.getFirst();
        for (int i = 1; i < converted.size(); i++) {
            result = new And(result, converted.get(i));
        }
        return result;
    }

    private Filter toLangChainFilter(String indexName, SearchFilter filter) {
        validateSearchFilter(indexName, filter);
        return switch (filter.getOperator()) {
            case EQ -> new IsEqualTo(filter.getField(), filter.getValue());
            case NE -> new IsNotEqualTo(filter.getField(), filter.getValue());
            case IN -> new IsIn(filter.getField(), filter.getValues());
            case NOT_IN -> new IsNotIn(filter.getField(), filter.getValues());
            case CONTAINS_STRING -> new ContainsString(filter.getField(), String.valueOf(filter.getValue()));
            // TAG字段由索引schema决定，业务侧只表达“包含该tag”的意图。
            case TAG_CONTAINS -> new IsEqualTo(filter.getField(), filter.getValue());
        };
    }

    private void validateSearchFilter(String indexName, SearchFilter filter) {
        if (!hasText(filter.getField())) {
            throw new IllegalArgumentException("RAG检索过滤字段不能为空");
        }
        validateMetadataField(filter.getField());
        RagProperties.Index index = ragProperties.getIndexes().get(indexName);
        if (!index.getMetadataFields().containsKey(filter.getField())) {
            throw new IllegalArgumentException("RAG索引未配置过滤字段: " + indexName + "." + filter.getField());
        }
        if (filter.getOperator() == null) {
            throw new IllegalArgumentException("RAG检索过滤操作符不能为空");
        }
        switch (filter.getOperator()) {
            case EQ, NE, TAG_CONTAINS, CONTAINS_STRING -> {
                if (filter.getValue() == null) {
                    throw new IllegalArgumentException("RAG检索过滤值不能为空: " + filter.getField());
                }
            }
            case IN, NOT_IN -> {
                if (filter.getValues() == null || filter.getValues().isEmpty()) {
                    throw new IllegalArgumentException("RAG检索过滤值列表不能为空: " + filter.getField());
                }
            }
        }
    }

    private List<String> searchQueries(String query, List<String> queries) {
        List<String> result = new ArrayList<>();
        if (queries != null) {
            for (String item : queries) {
                if (!hasText(item)) {
                    continue;
                }
                String normalized = item.trim();
                if (!result.contains(normalized)) {
                    result.add(normalized);
                }
            }
        }
        if (result.isEmpty() && hasText(query)) {
            result.add(query.trim());
        }
        return result;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void validateMetadataField(String fieldName) {
        if (!hasText(fieldName) || !METADATA_FIELD_PATTERN.matcher(fieldName).matches()) {
            throw new IllegalArgumentException("非法RAG元数据字段名: " + fieldName);
        }
    }
}
