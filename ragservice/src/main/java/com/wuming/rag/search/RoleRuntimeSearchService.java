package com.wuming.rag.search;

import com.wuming.api.rag.dto.PassageSearchRequest;
import com.wuming.api.rag.dto.RagHitDto;
import com.wuming.api.rag.dto.ReactionRuleSearchRequest;
import com.wuming.api.rag.dto.RoleExampleSearchRequest;
import com.wuming.api.rag.dto.SearchResult;
import com.wuming.rag.config.RagServiceProperties;
import com.wuming.rag.rerank.RerankDocument;
import com.wuming.rag.rerank.RerankService;
import com.wuming.rag.rerank.RerankedDocument;
import com.wuming.rag.vector.redis.RedisVectorIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleRuntimeSearchService {
    private final RedisVectorIndexService vectorIndexService;
    private final RerankService rerankService;
    private final RagServiceProperties properties;

    public SearchResult searchPassages(PassageSearchRequest request) {
        requireText(request.getIndexName(), "indexName不能为空");
        requireText(request.getQuery(), "query不能为空");
        if (request.getNovelId() == null) {
            throw new IllegalArgumentException("novelId不能为空");
        }
        return SearchResult.success(search(
                request.getIndexName(),
                request.getQuery(),
                Map.of("novelId", request.getNovelId()),
                request.getTopK()
        ));
    }

    public SearchResult searchRoleExamples(RoleExampleSearchRequest request) {
        requireText(request.getIndexName(), "indexName不能为空");
        requireText(request.getQuery(), "query不能为空");
        if (request.getCharacterId() == null) {
            throw new IllegalArgumentException("characterId不能为空");
        }
        return SearchResult.success(search(
                request.getIndexName(),
                request.getQuery(),
                Map.of("characterId", request.getCharacterId()),
                request.getTopK()
        ));
    }

    public SearchResult searchReactionRules(ReactionRuleSearchRequest request) {
        requireText(request.getIndexName(), "indexName不能为空");
        requireText(request.getQuery(), "query不能为空");
        if (request.getCharacterId() == null) {
            throw new IllegalArgumentException("characterId不能为空");
        }
        return SearchResult.success(search(
                request.getIndexName(),
                request.getQuery(),
                Map.of("characterId", request.getCharacterId()),
                request.getTopK()
        ));
    }

    private List<RagHitDto> search(String indexName,
                                   String query,
                                   Map<String, Object> filters,
                                   Integer topK) {
        int limit = topK == null ? properties.getRetrieve().getDefaultTopK() : topK;
        List<RagHitDto> hits = vectorIndexService.search(indexName, query, filters, limit);
        if (!properties.getReranker().isEnabled() || hits.isEmpty()) {
            return hits;
        }
        try {
            return rerank(query, hits);
        } catch (RuntimeException e) {
            log.warn("rerank失败，降级使用向量召回排序，indexName: {}", indexName, e);
            return hits;
        }
    }

    private List<RagHitDto> rerank(String query, List<RagHitDto> hits) {
        Map<String, RagHitDto> hitMap = hits.stream()
                .collect(Collectors.toMap(RagHitDto::getDocumentId, Function.identity()));
        List<RerankedDocument> reranked = rerankService.rerank(
                query,
                hits.stream()
                        .map(hit -> new RerankDocument(hit.getDocumentId(), hit.getText()))
                        .toList()
        );
        return reranked.stream()
                .filter(document -> document.score() >= properties.getRetrieve().getMinScore())
                .sorted(Comparator.comparingDouble(RerankedDocument::score).reversed())
                .map(document -> {
                    RagHitDto hit = hitMap.get(document.documentId());
                    hit.setScore(document.score());
                    return hit;
                })
                .toList();
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
