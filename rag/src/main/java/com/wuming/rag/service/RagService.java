package com.wuming.rag.service;

import com.wuming.api.rag.RagFacade;
import com.wuming.api.rag.dto.*;
import com.wuming.api.rag.dto.spec.PassageSearchRequest;
import com.wuming.api.rag.dto.spec.ReactionRuleSearchRequest;
import com.wuming.api.rag.dto.spec.RoleExampleSearchRequest;
import com.wuming.rag.rerank.RerankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


@Slf4j
@DubboService
@RequiredArgsConstructor
public class RagService implements RagFacade {
    private final RagVectorStoreRegistry registry;
    private final RerankService rerankService;

    @Override
    public int upsertDocuments(UpsertDocumentRequest request) {
        if (request.getDocuments() == null || request.getDocuments().isEmpty()) {
            return 0;
        }
        VectorStore store = registry.getRequired(request.getIndexName());
        List<Document> documents = request.getDocuments().stream()
                .filter(Objects::nonNull)
                .map(this::toSpringDocument)
                .toList();
        if (documents.isEmpty()) {
            return 0;
        }
        store.add(documents);
        return documents.size();
    }

    @Override
    public int deleteDocuments(DeleteDocumentRequest request) {
        if (request.getDocumentIds() == null || request.getDocumentIds().isEmpty()) {
            return 0;
        }
        VectorStore store = registry.getRequired(request.getIndexName());
        store.delete(request.getDocumentIds());
        return request.getDocumentIds().size();
    }

    @Override
    public List<SearchHit> searchPassages(PassageSearchRequest request) {
        return search(
                request.getIndexName(),
                request.getQuery(),
                Map.of("novel_id", request.getNovelId()),
                request.getTopK(),
                request.isRerank(),
                request.getTopN()
        );
    }

    @Override
    public List<SearchHit> searchRoleExamples(RoleExampleSearchRequest request) {
        return search(
                request.getIndexName(),
                request.getQuery(),
                Map.of("character_id", request.getCharacterId()),
                request.getTopK(),
                request.isRerank(),
                request.getTopN()
        );
    }

    @Override
    public List<SearchHit> searchReactionRules(ReactionRuleSearchRequest request) {
        return search(
                request.getIndexName(),
                request.getQuery(),
                Map.of("character_id", request.getCharacterId()),
                request.getTopK(),
                request.isRerank(),
                request.getTopN()
        );
    }

    private List<SearchHit> search(String indexName,
                                   String query,
                                   Map<String, Object> filters,
                                   Integer topK,
                                   boolean isRerank,
                                   Integer topN) {
        VectorStore store = registry.getRequired(indexName);
        int recallCount = topK == null ? 20 : topK;
        int rerankCount = topN == null ? recallCount : topN;

        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(recallCount);
        String filterExpression = filterExpression(filters);
        if (filterExpression != null) {
            builder.filterExpression(filterExpression);
        }
        SearchRequest searchRequest = builder.build();

        List<Document> documents = store.similaritySearch(searchRequest);
        List<Document> finalDocuments = isRerank
                ? rerankService.rerank(query, documents)
                : documents;
        return finalDocuments.stream()
                .limit(rerankCount)
                .map(this::toSearchHit)
                .toList();
    }

    private Document toSpringDocument(RagDocument document) {
        Map<String, Object> metadata = document.getMetadata() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(document.getMetadata());
        return new Document(document.getDocumentId(), document.getContent(), metadata);
    }

    private SearchHit toSearchHit(Document document) {
        SearchHit hit = new SearchHit();
        hit.setDocumentId(document.getId());
        hit.setContent(document.getText());
        hit.setScore(document.getScore() == null ? 0.0 : document.getScore());
        hit.setMetadata(new LinkedHashMap<>(document.getMetadata()));
        return hit;
    }

    private String filterExpression(Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        return filters.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .map(entry -> entry.getKey() + " == " + filterValue(entry.getValue()))
                .reduce((left, right) -> left + " && " + right)
                .orElse(null);
    }

    private String filterValue(Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return "'" + String.valueOf(value).replace("'", "\\'") + "'";
    }
}
