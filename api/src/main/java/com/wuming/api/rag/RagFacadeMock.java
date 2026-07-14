package com.wuming.api.rag;

import com.wuming.api.rag.dto.DeleteDocumentRequest;
import com.wuming.api.rag.dto.SearchHit;
import com.wuming.api.rag.dto.UpdateDocumentMetadataRequest;
import com.wuming.api.rag.dto.UpsertDocumentRequest;
import com.wuming.api.rag.dto.spec.SearchRequest;

import java.util.List;

/**
 * RAG 远程接口降级实现。
 *
 * <p>供 Dubbo consumer mock 使用。当 RAG 服务不可用、超时或远程调用异常时，
 * 写入接口返回-1，查询接口返回空结果，避免阻塞主流程。</p>
 */
public class RagFacadeMock implements RagFacade {

    @Override
    public int upsertDocuments(UpsertDocumentRequest request) {
        return -1;
    }

    @Override
    public int deleteDocuments(DeleteDocumentRequest request) {
        return -1;
    }

    @Override
    public int updateDocumentMetadata(UpdateDocumentMetadataRequest request) {
        return -1;
    }

    @Override
    public List<SearchHit> search(SearchRequest request) {
        return List.of();
    }
}
