package com.wuming.api.rag;

import com.wuming.api.rag.dto.PassageSearchRequest;
import com.wuming.api.rag.dto.ReactionRuleSearchRequest;
import com.wuming.api.rag.dto.RoleExampleSearchRequest;
import com.wuming.api.rag.dto.SearchResult;

public interface RoleRuntimeRagFacade {

    /**
     * 按小说 ID 检索原文片段。
     *
     * @param request 原文片段检索请求
     * @return 排序后的原文片段命中结果
     */
    SearchResult searchPassages(PassageSearchRequest request);

    /**
     * 按角色 ID 检索角色样例。
     *
     * @param request 角色样例检索请求
     * @return 排序后的角色样例命中结果
     */
    SearchResult searchRoleExamples(RoleExampleSearchRequest request);

    /**
     * 按角色 ID 检索角色反应规则。
     *
     * @param request 反应规则检索请求
     * @return 排序后的反应规则命中结果
     */
    SearchResult searchReactionRules(ReactionRuleSearchRequest request);
}
