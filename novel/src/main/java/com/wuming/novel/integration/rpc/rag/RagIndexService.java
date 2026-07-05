package com.wuming.novel.integration.rpc.rag;

import com.wuming.api.rag.RagFacade;
import com.wuming.api.rag.dto.RagDocument;
import com.wuming.api.rag.dto.UpsertDocumentRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class RagIndexService {
    @DubboReference(url = "${novel.rag.url:tri://127.0.0.1:50053}", timeout = 60000, retries = 0)
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
}
