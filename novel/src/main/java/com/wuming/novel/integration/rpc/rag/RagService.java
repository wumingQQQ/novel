package com.wuming.novel.integration.rpc.rag;

import com.wuming.api.rag.RagFacade;
import com.wuming.api.rag.dto.DeleteDocumentRequest;
import com.wuming.api.rag.dto.RagDocument;
import com.wuming.api.rag.dto.RerankDocumentsRequest;
import com.wuming.api.rag.dto.SearchHit;
import com.wuming.api.rag.dto.UpsertDocumentRequest;
import com.wuming.api.rag.dto.spec.PassageSearchRequest;
import com.wuming.api.rag.dto.spec.ReactionRuleSearchRequest;
import com.wuming.api.rag.dto.spec.RoleExampleSearchRequest;
import com.wuming.api.rag.dto.spec.SearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class RagService {
    @DubboReference(
            url = "${novel.rag.url:tri://127.0.0.1:50053}",
            timeout = 60000,
            retries = 0,
            mock = "com.wuming.api.rag.RagFacadeMock"
    )
    private RagFacade ragFacade;

    public int upsertDocuments(String indexName, List<RagDocument> documents) {
        requireText(indexName, "indexName不能为空");
        UpsertDocumentRequest request = new UpsertDocumentRequest();
        request.setIndexName(indexName);
        request.setDocuments(documents);
        int count = ragFacade.upsertDocuments(request);
        log.debug("RAG文档写入完成，indexName: {}, requestCount: {}, upsertCount: {}",
                indexName, documents.size(), count);
        return count;
    }

    public int deleteDocuments(String indexName, List<String> documentIds) {
        requireText(indexName, "indexName不能为空");
        DeleteDocumentRequest request = new DeleteDocumentRequest();
        request.setIndexName(indexName);
        request.setDocumentIds(documentIds);
        int count = ragFacade.deleteDocuments(request);
        log.debug("RAG文档删除完成，indexName: {}, requestCount: {}, deleteCount: {}",
                indexName, documentIds.size(), count);
        return count;
    }

    /**
     * 将有限候选文档交给 RAG 服务重排序，不触发额外向量召回。
     *
     * @param query 相关性查询文本
     * @param documents 待重排序文档
     * @return 按相关性降序排列的命中；RAG 降级时返回空列表
     */
    public List<SearchHit> rerankDocuments(String query, List<RagDocument> documents) {
        String normalizedQuery = requireText(query, "query不能为空");
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        RerankDocumentsRequest request = new RerankDocumentsRequest();
        request.setQuery(normalizedQuery);
        request.setDocuments(documents);
        request.setTopN(documents.size());
        List<SearchHit> hits = ragFacade.rerankDocuments(request);
        log.debug("RAG有限文档重排序完成，documentCount: {}, hitCount: {}", documents.size(), hits.size());
        return hits;
    }

    public List<SearchHit> search(SearchRequest request) {
        validateParam(request);

        List<SearchHit> hits;
        switch (request) {
            case PassageSearchRequest r -> {
                if (r.getNovelId() == null) {
                    throw new IllegalArgumentException("novelId不能为null");
                }
                hits = ragFacade.searchPassages(r);
                log.debug("RAG文档召回完成，indexName: {}, novelId: {}, queryCount: {}, topK: {}, topN: {}, rerank: {}, hitCount: {}",
                        r.getIndexName(), r.getNovelId(), queryCount(r), r.getTopK(), r.getTopN(), r.isRerank(), hits.size());
            }
            case RoleExampleSearchRequest r -> {
                if (r.getCharacterId() == null) {
                    throw new IllegalArgumentException("characterId不能为null");
                }
                hits = ragFacade.searchRoleExamples(r);
                log.debug("RAG角色样本召回完成，indexName: {}, characterId: {}, queryCount: {}, topK: {}, topN: {}, rerank: {}, hitCount: {}",
                        r.getIndexName(), r.getCharacterId(), queryCount(r), r.getTopK(), r.getTopN(), r.isRerank(), hits.size());
            }
            case ReactionRuleSearchRequest r -> {
                if (r.getCharacterId() == null) {
                    throw new IllegalArgumentException("characterId不能为null");
                }
                hits = ragFacade.searchReactionRules(r);
                log.debug("RAG角色反应规则召回完成，indexName: {}, characterId: {}, queryCount: {}, topK: {}, topN: {}, rerank: {}, hitCount: {}",
                        r.getIndexName(), r.getCharacterId(), queryCount(r), r.getTopK(), r.getTopN(), r.isRerank(), hits.size());
            }
            default -> {
                throw new IllegalArgumentException("不支持的RAG检索请求类型: " + request.getClass().getName());
            }
        }

        return hits;
    }

    private void validateParam(SearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("searchRequest不能为空");
        }
        request.setIndexName(requireText(request.getIndexName(), "indexName不能为空"));
        request.setQueries(normalizeQueries(request.getQueries()));
        request.setQuery(normalizeQuery(request.getQuery()));
        if (request.getQuery() == null && request.getQueries() == null) {
            throw new IllegalArgumentException("query和queries不能同时为空");
        }
        request.setTopK(normalizeTopK(request.getTopK()));
        request.setTopN(normalizeTopN(request.getTopN(), request.getTopK()));
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private int normalizeTopK(Integer topK) {
        return topK == null || topK <= 0 ? 20 : topK;
    }

    private int normalizeTopN(Integer topN, int topK) {
        return topN == null || topN <= 0 ? topK : topN;
    }

    private String normalizeQuery(String query) {
        return query == null || query.isBlank() ? null : query.trim();
    }

    private List<String> normalizeQueries(List<String> queries) {
        if (queries == null || queries.isEmpty()) {
            return null;
        }
        List<String> normalized = new ArrayList<>();
        for (String query : queries) {
            if (query == null || query.isBlank()) {
                continue;
            }
            String value = query.trim();
            if (!normalized.contains(value)) {
                normalized.add(value);
            }
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private int queryCount(SearchRequest request) {
        if (request.getQueries() != null) {
            return request.getQueries().size();
        }
        return request.getQuery() == null ? 0 : 1;
    }
}
