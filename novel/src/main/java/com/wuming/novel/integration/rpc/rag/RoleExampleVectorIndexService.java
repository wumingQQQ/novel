package com.wuming.novel.integration.rpc.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.api.rag.dto.RagDocument;
import com.wuming.api.rag.dto.SearchHit;
import com.wuming.api.rag.dto.spec.SearchFilter;
import com.wuming.api.rag.dto.spec.SearchRequest;
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
        return search(characterId, query, queries, topK, rerank, topN, null);
    }

    /**
     * 按角色检索向量样本，并可在评测场景排除指定原作文本块。
     *
     * @param characterId 目标角色主键
     * @param query 主检索语句
     * @param queries 多路检索语句
     * @param topK 初始召回数量
     * @param rerank 是否执行重排序
     * @param topN 最终保留数量
     * @param excludedPassageId 需要排除的来源Passage主键，正常检索时为null
     * @return 过滤、召回和重排序后的命中列表
     */
    public List<SearchHit> search(Long characterId,
                                  String query,
                                  List<String> queries,
                                  Integer topK,
                                  boolean rerank,
                                  Integer topN,
                                  Long excludedPassageId) {
        if (excludedPassageId != null && excludedPassageId <= 0) {
            throw new IllegalArgumentException("excludedPassageId必须为正数");
        }
        SearchRequest request = new SearchRequest();
        request.setIndexName(roleExampleIndexName);
        request.setQuery(query);
        request.setQueries(queries);
        request.setFilters(roleExampleFilters(characterId, excludedPassageId));
        request.setTopK(topK);
        request.setRerank(rerank);
        request.setTopN(topN);
        return ragService.search(request);
    }

    private List<SearchFilter> roleExampleFilters(Long characterId, Long excludedPassageId) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId不能为空");
        }
        List<SearchFilter> filters = new java.util.ArrayList<>();
        filters.add(SearchFilter.eq("character_id", characterId));
        if (excludedPassageId != null) {
            filters.add(SearchFilter.ne("passage_id", excludedPassageId));
        }
        return filters;
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
