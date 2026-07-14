package com.wuming.novel.integration.rpc.rag;

import com.wuming.api.rag.RagFacade;
import com.wuming.api.rag.dto.DeleteDocumentRequest;
import com.wuming.api.rag.dto.RagDocument;
import com.wuming.api.rag.dto.SearchHit;
import com.wuming.api.rag.dto.UpdateDocumentMetadataRequest;
import com.wuming.api.rag.dto.UpsertDocumentRequest;
import com.wuming.api.rag.dto.spec.SearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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

    public int updateDocumentMetadata(String indexName, String documentId, Map<String, Object> metadata) {
        requireText(indexName, "indexName不能为空");
        requireText(documentId, "documentId不能为空");
        if (metadata == null || metadata.isEmpty()) {
            return 0;
        }

        UpdateDocumentMetadataRequest.DocumentMetadataPatch patch =
                new UpdateDocumentMetadataRequest.DocumentMetadataPatch();
        patch.setDocumentId(documentId);
        patch.setMetadata(metadata);

        UpdateDocumentMetadataRequest request = new UpdateDocumentMetadataRequest();
        request.setIndexName(indexName);
        request.setPatches(List.of(patch));

        int count = ragFacade.updateDocumentMetadata(request);
        log.debug("RAG文档元数据更新完成，indexName: {}, documentId: {}, fieldCount: {}, updateCount: {}",
                indexName, documentId, metadata.size(), count);
        return count;
    }

    public List<SearchHit> search(SearchRequest request) {
        validateParam(request);

        List<SearchHit> hits = ragFacade.search(request);
        log.debug("RAG通用召回完成，indexName: {}, filterCount: {}, topK: {}, topN: {}, multiQuery: {}, hitCount: {}",
                request.getIndexName(),
                request.getFilters() == null ? 0 : request.getFilters().size(),
                request.getTopK(), request.getTopN(), request.isMultiQuery(), hits.size());
        return hits;
    }

    private void validateParam(SearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("searchRequest不能为空");
        }
        request.setIndexName(requireText(request.getIndexName(), "indexName不能为空"));
        request.setQuery(normalizeQuery(request.getQuery()));
        if (request.getQuery() == null) {
            throw new IllegalArgumentException("query不能为空");
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
}
