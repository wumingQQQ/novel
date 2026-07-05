package com.wuming.api.rag;

import com.wuming.api.rag.dto.CreateIndexRequest;
import com.wuming.api.rag.dto.DeleteDocumentsRequest;
import com.wuming.api.rag.dto.DeleteResult;
import com.wuming.api.rag.dto.IndexResult;
import com.wuming.api.rag.dto.UpsertDocumentsRequest;
import com.wuming.api.rag.dto.UpsertResult;

public interface RagIndexFacade {

    /**
     * Idempotently creates the logical vector index backing the given RAG type.
     *
     * @param request logical index creation request
     * @return operation result with CREATED or EXISTS on success
     */
    IndexResult createIndex(CreateIndexRequest request);

    /**
     * Upserts vector documents into the logical index.
     *
     * @param request documents to index
     * @return operation result with indexed count
     */
    UpsertResult upsertDocuments(UpsertDocumentsRequest request);

    /**
     * Deletes vector documents from the logical index by stable document id.
     *
     * @param request document ids to delete
     * @return operation result with requested delete count
     */
    DeleteResult deleteDocuments(DeleteDocumentsRequest request);
}
