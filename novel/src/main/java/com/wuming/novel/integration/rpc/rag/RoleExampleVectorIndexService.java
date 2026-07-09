package com.wuming.novel.integration.rpc.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.api.rag.dto.RagDocument;
import com.wuming.api.rag.dto.SearchHit;
import com.wuming.api.rag.dto.spec.RoleExampleSearchRequest;
import com.wuming.novel.domain.entity.RoleExample;
import com.wuming.novel.infrastructure.mapper.RoleExampleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色原作样本向量索引服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleExampleVectorIndexService {
    private static final String VECTOR_PENDING = "PENDING";
    private static final String VECTOR_INDEXED = "INDEXED";
    private static final String VECTOR_FAILED = "FAILED";

    private final RagService ragService;
    private final RoleExampleMapper roleExampleMapper;

    @Value("${novel.rag.role-example-index-name:role_example}")
    private String roleExampleIndexName;

    public int upsertDocuments(List<RoleExample> examples) {
        List<RagDocument> documents = examples.stream()
                .map(this::toDocument)
                .toList();
        return ragService.upsertDocuments(roleExampleIndexName, documents);
    }

    public int deleteByIds(Long characterId, List<Long> exampleIds) {
        if (exampleIds == null || exampleIds.isEmpty()) {
            return 0;
        }
        List<String> documentIds = exampleIds.stream()
                .map(exampleId -> exampleDocumentId(characterId, exampleId))
                .toList();
        return ragService.deleteDocuments(roleExampleIndexName, documentIds);
    }

    public List<SearchHit> search(Long characterId,
                                  String query,
                                  Integer topK,
                                  boolean rerank,
                                  Integer topN) {
        return search(characterId, query, null, topK, rerank, topN);
    }

    public List<SearchHit> search(Long characterId,
                                  String query,
                                  List<String> queries,
                                  Integer topK,
                                  boolean rerank,
                                  Integer topN) {
        RoleExampleSearchRequest request = new RoleExampleSearchRequest();
        request.setIndexName(roleExampleIndexName);
        request.setCharacterId(characterId);
        request.setQuery(query);
        request.setQueries(queries);
        request.setTopK(topK);
        request.setRerank(rerank);
        request.setTopN(topN);
        return ragService.search(request);
    }

    /**
     * 按样本主键写入向量库，并回写索引状态。
     *
     * @param exampleIds 待索引样本主键
     * @return 成功写入向量库的文档数量
     */
    public int indexByIds(List<Long> exampleIds) {
        if (exampleIds == null || exampleIds.isEmpty()) {
            return 0;
        }
        List<RoleExample> examples = roleExampleMapper.selectList(new LambdaQueryWrapper<RoleExample>()
                .in(RoleExample::getId, exampleIds)
                .in(RoleExample::getVectorStatus, VECTOR_PENDING, VECTOR_FAILED));
        if (examples.isEmpty()) {
            log.debug("没有需要索引的角色样本，requestCount: {}", exampleIds.size());
            return 0;
        }
        try {
            int indexedCount = upsertDocuments(examples);
            if (indexedCount < 0) {
                markFailed(examples, "RAG服务降级，角色样本未写入向量库");
                log.warn("RAG服务降级，角色样本未写入向量库，requestCount: {}", examples.size());
                return indexedCount;
            }
            if (indexedCount != examples.size()) {
                throw new IllegalStateException("角色样本向量索引数量不一致，requestCount: "
                        + examples.size() + ", indexedCount: " + indexedCount);
            }
            examples.forEach(example -> {
                example.setVectorStatus(VECTOR_INDEXED);
                example.setVectorError(null);
                example.setIndexedTime(LocalDateTime.now());
            });
            updateById(examples);
            return indexedCount;
        } catch (RuntimeException e) {
            markFailed(examples, e.getMessage());
            throw e;
        }
    }

    private void markFailed(List<RoleExample> examples, String errorMessage) {
        examples.forEach(example -> {
            example.setVectorStatus(VECTOR_FAILED);
            example.setVectorError(errorMessage);
        });
        updateById(examples);
    }

    private void updateById(List<RoleExample> examples) {
        examples.forEach(roleExampleMapper::updateById);
    }

    private RagDocument toDocument(RoleExample example) {
        RagDocument document = new RagDocument();
        document.setDocumentId(exampleDocumentId(example.getCharacterId(), example.getId()));
        document.setContent(example.getSampleText());
        document.setMetadata(metadata(example));
        return document;
    }

    private String exampleDocumentId(Long characterId, Long exampleId) {
        return "character:%s:example:%s".formatted(characterId, exampleId);
    }

    private Map<String, Object> metadata(RoleExample example) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("character_id", example.getCharacterId());
        metadata.put("character_name", example.getCharacterName());
        metadata.put("example_id", example.getId());
        metadata.put("passage_id", example.getPassageId());
        metadata.put("sample_type", example.getSampleType());
        return metadata;
    }
}
