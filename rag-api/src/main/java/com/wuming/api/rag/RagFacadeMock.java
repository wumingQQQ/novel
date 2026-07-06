package com.wuming.api.rag;

import com.wuming.api.rag.dto.DeleteDocumentRequest;
import com.wuming.api.rag.dto.SearchHit;
import com.wuming.api.rag.dto.UpsertDocumentRequest;
import com.wuming.api.rag.dto.spec.PassageSearchRequest;
import com.wuming.api.rag.dto.spec.ReactionRuleSearchRequest;
import com.wuming.api.rag.dto.spec.RoleExampleSearchRequest;

import java.util.List;
import java.util.Objects;

/**
 * RAG 远程接口降级实现。
 *
 * <p>供 Dubbo consumer mock 使用。当 RAG 服务不可用、超时或远程调用异常时，
 * 返回不阻塞主流程的默认值。</p>
 */
public class RagFacadeMock implements RagFacade {

    @Override
    public int upsertDocuments(UpsertDocumentRequest request) {
        if (request == null || request.getDocuments() == null) {
            return 0;
        }
        return (int) request.getDocuments().stream()
                .filter(Objects::nonNull)
                .count();
    }

    @Override
    public int deleteDocuments(DeleteDocumentRequest request) {
        if (request == null || request.getDocumentIds() == null) {
            return 0;
        }
        return request.getDocumentIds().size();
    }

    @Override
    public List<SearchHit> searchPassages(PassageSearchRequest request) {
        return List.of();
    }

    @Override
    public List<SearchHit> searchRoleExamples(RoleExampleSearchRequest request) {
        return List.of();
    }

    @Override
    public List<SearchHit> searchReactionRules(ReactionRuleSearchRequest request) {
        return List.of();
    }
}
