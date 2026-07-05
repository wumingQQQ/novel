package com.wuming.api.rag;

import com.wuming.api.rag.dto.PassageSearchRequest;
import com.wuming.api.rag.dto.ReactionRuleSearchRequest;
import com.wuming.api.rag.dto.RoleExampleSearchRequest;
import com.wuming.api.rag.dto.SearchResult;

public interface RoleRuntimeRagFacade {

    /**
     * Searches source novel passages by novel id.
     *
     * @param request passage search request
     * @return ranked passage hits
     */
    SearchResult searchPassages(PassageSearchRequest request);

    /**
     * Searches role examples by character id.
     *
     * @param request role example search request
     * @return ranked role example hits
     */
    SearchResult searchRoleExamples(RoleExampleSearchRequest request);

    /**
     * Searches role reaction rules by character id.
     *
     * @param request reaction rule search request
     * @return ranked reaction rule hits
     */
    SearchResult searchReactionRules(ReactionRuleSearchRequest request);
}
