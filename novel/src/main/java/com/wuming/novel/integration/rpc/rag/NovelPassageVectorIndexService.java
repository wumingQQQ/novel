package com.wuming.novel.integration.rpc.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.api.rag.dto.RagDocument;
import com.wuming.novel.domain.entity.NovelPassage;
import com.wuming.novel.infrastructure.mapper.NovelPassageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NovelPassageVectorIndexService {
    private static final String VECTOR_PENDING = "PENDING";
    private static final String VECTOR_INDEXED = "INDEXED";
    private static final String VECTOR_FAILED = "FAILED";

    private final RagService ragService;
    private final NovelPassageMapper novelPassageMapper;

    @Value("${novel.rag.passage-index-name:novel_passage}")
    private String passageIndexName;

    public int upsertDocuments(List<NovelPassage> passages) {
        List<RagDocument> documents = passages.stream()
                .map(this::toDocument)
                .toList();
        return ragService.upsertDocuments(passageIndexName, documents);
    }

    public int deleteByIds(Long novelId, List<Long> passageIds) {
        if (passageIds == null || passageIds.isEmpty()) {
            return 0;
        }
        List<String> documentIds = passageIds.stream()
                .map(passageId -> passageDocumentId(novelId, passageId))
                .toList();
        return ragService.deleteDocuments(passageIndexName, documentIds);
    }

    /**
     * 按Passage主键写入向量库，并回写索引状态。
     *
     * @param passageIds 待索引Passage主键
     * @return 成功写入向量库的文档数量
     */
    public int indexByIds(List<Long> passageIds) {
        if (passageIds == null || passageIds.isEmpty()) {
            return 0;
        }
        List<NovelPassage> passages = novelPassageMapper.selectList(new LambdaQueryWrapper<NovelPassage>()
                .in(NovelPassage::getId, passageIds)
                .in(NovelPassage::getVectorStatus, VECTOR_PENDING, VECTOR_FAILED));
        if (passages.isEmpty()) {
            log.info("没有需要索引的Passage，requestCount: {}", passageIds.size());
            return 0;
        }
        try {
            int indexedCount = upsertDocuments(passages);
            if (indexedCount < 0) {
                markFailed(passages, "RAG服务降级，Passage未写入向量库");
                log.warn("RAG服务降级，Passage未写入向量库，requestCount: {}", passages.size());
                return indexedCount;
            }
            if (indexedCount != passages.size()) {
                throw new IllegalStateException("Passage向量索引数量不一致，requestCount: "
                        + passages.size() + ", indexedCount: " + indexedCount);
            }
            passages.forEach(passage -> {
                passage.setVectorStatus(VECTOR_INDEXED);
                passage.setVectorError(null);
                passage.setIndexedTime(LocalDateTime.now());
            });
            updateById(passages);
            return indexedCount;
        } catch (RuntimeException e) {
            markFailed(passages, e.getMessage());
            throw e;
        }
    }

    private void markFailed(List<NovelPassage> passages, String errorMessage) {
        passages.forEach(passage -> {
            passage.setVectorStatus(VECTOR_FAILED);
            passage.setVectorError(errorMessage);
        });
        updateById(passages);
    }

    private void updateById(List<NovelPassage> passages) {
        passages.forEach(novelPassageMapper::updateById);
    }

    private RagDocument toDocument(NovelPassage passage) {
        RagDocument document = new RagDocument();
        document.setDocumentId(passageDocumentId(passage.getNovelId(), passage.getId()));
        document.setContent(passage.getContent());
        document.setMetadata(metadata(passage));
        return document;
    }

    private String passageDocumentId(Long novelId, Long passageId) {
        return "novel:%s:passage:%s".formatted(novelId, passageId);
    }

    private Map<String, Object> metadata(NovelPassage passage) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("novel_id", passage.getNovelId());
        metadata.put("chapter_id", passage.getChapterId());
        metadata.put("passage_id", passage.getId());
        metadata.put("passage_sequence", passage.getSequence());
        return metadata;
    }
}
