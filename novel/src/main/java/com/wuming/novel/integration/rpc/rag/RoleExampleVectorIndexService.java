package com.wuming.novel.integration.rpc.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.api.rag.dto.RagDocument;
import com.wuming.novel.domain.entity.RoleExample;
import com.wuming.novel.service.IRoleExampleService;
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
    private final IRoleExampleService roleExampleService;

    @Value("${novel.rag.role-example-index-name:role_example}")
    private String roleExampleIndexName;

    public int upsertDocuments(List<RoleExample> examples) {
        List<RagDocument> documents = examples.stream()
                .map(this::toDocument)
                .toList();
        return ragService.upsertDocuments(roleExampleIndexName, documents);
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
        List<RoleExample> examples = roleExampleService.list(new LambdaQueryWrapper<RoleExample>()
                .in(RoleExample::getId, exampleIds)
                .in(RoleExample::getVectorStatus, VECTOR_PENDING, VECTOR_FAILED));
        if (examples.isEmpty()) {
            log.info("没有需要索引的角色样本，requestCount: {}", exampleIds.size());
            return 0;
        }
        try {
            int indexedCount = upsertDocuments(examples);
            examples.forEach(example -> {
                example.setVectorStatus(VECTOR_INDEXED);
                example.setVectorError(null);
                example.setIndexedTime(LocalDateTime.now());
            });
            roleExampleService.updateBatchById(examples);
            return indexedCount;
        } catch (RuntimeException e) {
            examples.forEach(example -> {
                example.setVectorStatus(VECTOR_FAILED);
                example.setVectorError(e.getMessage());
            });
            roleExampleService.updateBatchById(examples);
            throw e;
        }
    }

    private RagDocument toDocument(RoleExample example) {
        RagDocument document = new RagDocument();
        document.setDocumentId(String.valueOf(example.getId()));
        document.setContent(example.getSampleText());
        document.setMetadata(metadata(example));
        return document;
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
