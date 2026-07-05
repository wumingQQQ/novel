package com.wuming.rag.integration.rpc;

import com.wuming.api.rag.RagIndexFacade;
import com.wuming.api.rag.dto.CreateIndexRequest;
import com.wuming.api.rag.dto.DeleteDocumentsRequest;
import com.wuming.api.rag.dto.DeleteResult;
import com.wuming.api.rag.dto.IndexResult;
import com.wuming.api.rag.dto.UpsertDocumentsRequest;
import com.wuming.api.rag.dto.UpsertResult;
import com.wuming.api.rag.enums.IndexStatus;
import com.wuming.rag.index.RagIndexDefinition;
import com.wuming.rag.vector.redis.RedisVectorIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@DubboService
@RequiredArgsConstructor
public class RagIndexFacadeImpl implements RagIndexFacade {
    private final RedisVectorIndexService vectorIndexService;

    @Override
    public IndexResult createIndex(CreateIndexRequest request) {
        try {
            RagIndexDefinition definition = toDefinition(request);
            IndexStatus status = vectorIndexService.createIndex(definition);
            return IndexResult.success(definition.getIndexName(), status);
        } catch (IllegalArgumentException e) {
            return IndexResult.failure(request == null ? null : request.getIndexName(),
                    "VALIDATION_ERROR", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("RAG索引创建失败，indexName: {}",
                    request == null ? null : request.getIndexName(), e);
            return IndexResult.failure(request == null ? null : request.getIndexName(),
                    "VECTOR_STORE_FAILED", e.getMessage());
        }
    }

    @Override
    public UpsertResult upsertDocuments(UpsertDocumentsRequest request) {
        try {
            requireIndexName(request == null ? null : request.getIndexName());
            int count = vectorIndexService.upsertDocuments(request.getIndexName(), request.getDocuments());
            return UpsertResult.success(count);
        } catch (IllegalArgumentException e) {
            return UpsertResult.failure("VALIDATION_ERROR", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("RAG文档写入失败，indexName: {}",
                    request == null ? null : request.getIndexName(), e);
            return UpsertResult.failure("VECTOR_STORE_FAILED", e.getMessage());
        }
    }

    @Override
    public DeleteResult deleteDocuments(DeleteDocumentsRequest request) {
        try {
            requireIndexName(request == null ? null : request.getIndexName());
            int count = vectorIndexService.deleteDocuments(request.getIndexName(), request.getDocumentIds());
            return DeleteResult.success(count);
        } catch (IllegalArgumentException e) {
            return DeleteResult.failure("VALIDATION_ERROR", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("RAG文档删除失败，indexName: {}",
                    request == null ? null : request.getIndexName(), e);
            return DeleteResult.failure("VECTOR_STORE_FAILED", e.getMessage());
        }
    }

    private RagIndexDefinition toDefinition(CreateIndexRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request不能为空");
        }
        RagIndexDefinition definition = new RagIndexDefinition();
        definition.setIndexName(request.getIndexName());
        definition.setKeyPrefix(request.getKeyPrefix());
        definition.setVectorDimension(request.getVectorDimension());
        Map<String, com.wuming.api.rag.enums.MetadataFieldType> fields = new LinkedHashMap<>();
        if (request.getMetadataFields() != null) {
            request.getMetadataFields().forEach(field -> fields.put(field.getName(), field.getType()));
        }
        definition.setMetadataFields(fields);
        return definition;
    }

    private void requireIndexName(String indexName) {
        if (indexName == null || indexName.isBlank()) {
            throw new IllegalArgumentException("indexName不能为空");
        }
    }
}
