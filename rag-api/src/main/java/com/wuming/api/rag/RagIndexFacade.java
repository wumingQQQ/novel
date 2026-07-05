package com.wuming.api.rag;

import com.wuming.api.rag.dto.CreateIndexRequest;
import com.wuming.api.rag.dto.DeleteDocumentsRequest;
import com.wuming.api.rag.dto.DeleteResult;
import com.wuming.api.rag.dto.IndexResult;
import com.wuming.api.rag.dto.UpsertDocumentsRequest;
import com.wuming.api.rag.dto.UpsertResult;

public interface RagIndexFacade {

    /**
     * 幂等创建动态 RAG 向量索引。
     *
     * @param request 索引创建请求
     * @return 操作结果，成功时返回 CREATED 或 EXISTS
     */
    IndexResult createIndex(CreateIndexRequest request);

    /**
     * 向指定索引写入或更新向量文档。
     *
     * @param request 待写入的文档请求
     * @return 操作结果，包含写入文档数量
     */
    UpsertResult upsertDocuments(UpsertDocumentsRequest request);

    /**
     * 按稳定文档 ID 从指定索引删除向量文档。
     *
     * @param request 待删除文档 ID 请求
     * @return 操作结果，包含删除文档数量
     */
    DeleteResult deleteDocuments(DeleteDocumentsRequest request);
}
