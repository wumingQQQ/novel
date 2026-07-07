package com.wuming.novel.integration.rpc.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.api.rag.dto.RagDocument;
import com.wuming.novel.domain.entity.NovelPassage;
import com.wuming.novel.service.INovelPassageService;
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
    private final INovelPassageService novelPassageService;

    @Value("${novel.rag.passage-index-name:novel_passage}")
    private String passageIndexName;

    public int upsertDocuments(List<NovelPassage> passages) {
        List<RagDocument> documents = passages.stream()
                .map(this::toDocument)
                .toList();
        return ragService.upsertDocuments(passageIndexName, documents);
    }

    /**
     * 按Passage主键异步写入向量库，并回写索引状态。
     *
     * @param passageIds 待索引Passage主键
     * @return 成功写入向量库的文档数量
     */
    public int indexByIds(List<Long> passageIds) {
        if (passageIds == null || passageIds.isEmpty()) {
            return 0;
        }
        List<NovelPassage> passages = novelPassageService.list(new LambdaQueryWrapper<NovelPassage>()
                .in(NovelPassage::getId, passageIds)
                .in(NovelPassage::getVectorStatus, VECTOR_PENDING, VECTOR_FAILED));
        if (passages.isEmpty()) {
            log.info("没有需要索引的Passage，requestCount: {}", passageIds.size());
            return 0;
        }
        try {
            int indexedCount = upsertDocuments(passages);
            if (indexedCount < 0) {
                throw new IllegalStateException("RAG服务降级，Passage未写入向量库");
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
            novelPassageService.updateBatchById(passages);
            return indexedCount;
        } catch (RuntimeException e) {
            passages.forEach(passage -> {
                passage.setVectorStatus(VECTOR_FAILED);
                passage.setVectorError(e.getMessage());
            });
            novelPassageService.updateBatchById(passages);
            throw e;
        }
    }

    private RagDocument toDocument(NovelPassage passage) {
        RagDocument document = new RagDocument();
        document.setDocumentId(String.valueOf(passage.getId()));
        document.setContent(passage.getContent());
        document.setMetadata(metadata(passage));
        return document;
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
