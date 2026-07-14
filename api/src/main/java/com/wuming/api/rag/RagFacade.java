package com.wuming.api.rag;

import com.wuming.api.rag.dto.*;
import com.wuming.api.rag.dto.spec.SearchRequest;

import java.util.List;

/**
 * RAG 服务对外接口。
 */
public interface RagFacade {


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
     * 局部更新指定索引中文档的元数据字段，不重新生成向量。
     *
     * @param request 文档元数据更新请求
     * @return 实际更新的文档数量
     */
    int updateDocumentMetadata(UpdateDocumentMetadataRequest request);

    /**
     * 按指定索引和元数据过滤条件执行通用向量检索。
     *
     * @param request 通用检索请求
     * @return 排序后的命中结果
     */
    List<SearchHit> search(SearchRequest request);
}
