package com.wuming.novel.integration.rpc.rag;

import com.wuming.api.rag.dto.RagDocument;
import com.wuming.novel.domain.entity.NovelPassage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NovelPassageVectorIndexService {
    private final RagIndexService ragIndexService;

    @Value("${novel.rag.passage-index-name:novel_passage}")
    private String passageIndexName;

    public int upsert(List<NovelPassage> passages) {
        List<RagDocument> documents = passages.stream()
                .map(this::toDocument)
                .toList();
        return ragIndexService.upsertDocuments(passageIndexName, documents);
    }

    private RagDocument toDocument(NovelPassage passage) {
        RagDocument document = new RagDocument();
        document.setDocumentId("novel_passage:" + passage.getId());
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
        metadata.put("chapter_sequence", passage.getChapterSequence());
        metadata.put("start_paragraph", passage.getStartParagraph());
        metadata.put("end_paragraph", passage.getEndParagraph());
        return metadata;
    }
}
