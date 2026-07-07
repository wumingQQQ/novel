package com.wuming.novel.integration.rpc.rag;

import com.wuming.api.rag.RagFacade;
import com.wuming.api.rag.dto.DeleteDocumentRequest;
import com.wuming.api.rag.dto.RagDocument;
import com.wuming.api.rag.dto.SearchHit;
import com.wuming.api.rag.dto.UpsertDocumentRequest;
import com.wuming.api.rag.dto.spec.PassageSearchRequest;
import com.wuming.api.rag.dto.spec.ReactionRuleSearchRequest;
import com.wuming.api.rag.dto.spec.RoleExampleSearchRequest;
import com.wuming.api.rag.dto.spec.SearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

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

    public List<SearchHit> search(SearchRequest request) {
        validateParam(request);

        List<SearchHit> hits;
        switch (request) {
            case PassageSearchRequest r -> {
                if (r.getNovelId() == null) {
                    throw new IllegalArgumentException("novelId不能为null");
                }
                hits = ragFacade.searchPassages(r);
                log.info("RAG文档召回，query: {}, retrieve count: {}", request.getQuery(), hits.size());
            }
            case RoleExampleSearchRequest r -> {
                if (r.getCharacterId() == null) {
                    throw new IllegalArgumentException("characterId不能为null");
                }
                hits = ragFacade.searchRoleExamples(r);
                log.info("RAG角色样本召回，characterId: {}, query: {}, retrieve count: {}",
                        r.getCharacterId(), request.getQuery(), hits.size());
            }
            case ReactionRuleSearchRequest r -> {
                if (r.getCharacterId() == null) {
                    throw new IllegalArgumentException("characterId不能为null");
                }
                hits = ragFacade.searchReactionRules(r);
                log.info("RAG角色反应规则召回，characterId: {}, query: {}, retrieve count: {}",
                        r.getCharacterId(), request.getQuery(), hits.size());
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
        request.setQuery(requireText(request.getQuery(), "query不能为空"));
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
}
