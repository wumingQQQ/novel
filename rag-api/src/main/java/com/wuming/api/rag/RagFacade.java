package com.wuming.api.rag;

import com.wuming.api.rag.dto.*;
import com.wuming.api.rag.dto.spec.PassageSearchRequest;
import com.wuming.api.rag.dto.spec.ReactionRuleSearchRequest;
import com.wuming.api.rag.dto.spec.RoleExampleSearchRequest;

import java.util.List;

/**
 * RAG 服务对外接口。
 */
public interface RagFacade {

    /**
     * 幂等创建动态 RAG 向量索引。
     *
     * @param request 索引创建请求
     * @return true 表示本次创建成功，false 表示创建失败
     */
    boolean createIndex(CreateIndexRequest request);

    /**
     * 向指定索引写入或更新向量文档。
     *
     * @param request 待写入的文档请求
     * @return 实际写入或更新的文档数量
     */
    int upsertDocuments(UpsertDocumentRequest request);

    /**
     * 按稳定文档 ID 从指定索引删除向量文档。
     *
     * @param request 待删除文档 ID 请求
     * @return 实际删除的文档数量
     */
    int deleteDocuments(DeleteDocumentRequest request);

    /**
     * 按小说 ID 检索原文片段。
     *
     * @param request 原文片段检索请求
     * @return 排序后的原文片段命中结果
     */
    List<SearchHit> searchPassages(PassageSearchRequest request);

    /**
     * 按角色 ID 检索角色样例。
     *
     * @param request 角色样例检索请求
     * @return 排序后的角色样例命中结果
     */
    List<SearchHit> searchRoleExamples(RoleExampleSearchRequest request);

    /**
     * 按角色 ID 检索角色反应规则。
     *
     * @param request 反应规则检索请求
     * @return 排序后的反应规则命中结果
     */
    List<SearchHit> searchReactionRules(ReactionRuleSearchRequest request);
}
