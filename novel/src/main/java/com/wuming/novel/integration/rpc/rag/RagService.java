package com.wuming.novel.integration.rpc.rag;

import com.wuming.api.rag.RagFacade;
import com.wuming.api.rag.dto.DeleteDocumentRequest;
import com.wuming.api.rag.dto.RagDocument;
import com.wuming.api.rag.dto.SearchHit;
import com.wuming.api.rag.dto.UpsertDocumentRequest;
import com.wuming.api.rag.dto.spec.PassageSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.ai.document.Document;
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
        UpsertDocumentRequest request = new UpsertDocumentRequest();
        request.setIndexName(indexName);
        request.setDocuments(documents);
        int count = ragFacade.upsertDocuments(request);
        log.debug("RAG文档写入完成，indexName: {}, requestCount: {}, upsertCount: {}",
                indexName, documents.size(), count);
        return count;
    }

    public int deleteDocuments(String indexName, List<String> documentIds) {
        DeleteDocumentRequest request = new DeleteDocumentRequest();
        request.setIndexName(indexName);
        request.setDocumentIds(documentIds);
        int count = ragFacade.deleteDocuments(request);
        log.debug("RAG文档删除完成，indexName: {}, requestCount: {}, deleteCount: {}",
                indexName, documentIds.size(), count);
        return count;
    }

    public List<SearchHit> search(String indexName, String query, Map<String, Object> params){
        PassageSearchRequest request = new PassageSearchRequest();
        request.setIndexName(indexName);
        request.setQuery(query);
        request.setNovelId((Long)params.get("novelId"));
        request.setTopK((Integer)params.get("topK"));
        request.setRerank((boolean)params.get("rerank"));
        request.setTopN((Integer)params.get("topN"));

        List<SearchHit> hits = ragFacade.searchPassages(request);
        log.info("RAG文档召回，query: {}, retrieve count: {}", query, hits.size());

        return hits;
    }
}
