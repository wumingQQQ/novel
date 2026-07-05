package com.wuming.rag.integration.rpc;

import com.wuming.api.rag.RoleRuntimeRagFacade;
import com.wuming.api.rag.dto.PassageSearchRequest;
import com.wuming.api.rag.dto.ReactionRuleSearchRequest;
import com.wuming.api.rag.dto.RoleExampleSearchRequest;
import com.wuming.api.rag.dto.SearchResult;
import com.wuming.rag.search.RoleRuntimeSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

@Slf4j
@DubboService
@RequiredArgsConstructor
public class RoleRuntimeRagFacadeImpl implements RoleRuntimeRagFacade {
    private final RoleRuntimeSearchService searchService;

    @Override
    public SearchResult searchPassages(PassageSearchRequest request) {
        try {
            return searchService.searchPassages(request);
        } catch (IllegalArgumentException e) {
            return SearchResult.failure("VALIDATION_ERROR", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("passage RAG检索失败，indexName: {}",
                    request == null ? null : request.getIndexName(), e);
            return SearchResult.failure("VECTOR_STORE_FAILED", e.getMessage());
        }
    }

    @Override
    public SearchResult searchRoleExamples(RoleExampleSearchRequest request) {
        try {
            return searchService.searchRoleExamples(request);
        } catch (IllegalArgumentException e) {
            return SearchResult.failure("VALIDATION_ERROR", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("role example RAG检索失败，indexName: {}",
                    request == null ? null : request.getIndexName(), e);
            return SearchResult.failure("VECTOR_STORE_FAILED", e.getMessage());
        }
    }

    @Override
    public SearchResult searchReactionRules(ReactionRuleSearchRequest request) {
        try {
            return searchService.searchReactionRules(request);
        } catch (IllegalArgumentException e) {
            return SearchResult.failure("VALIDATION_ERROR", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("reaction rule RAG检索失败，indexName: {}",
                    request == null ? null : request.getIndexName(), e);
            return SearchResult.failure("VECTOR_STORE_FAILED", e.getMessage());
        }
    }
}
