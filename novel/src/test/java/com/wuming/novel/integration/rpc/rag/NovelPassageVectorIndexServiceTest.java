package com.wuming.novel.integration.rpc.rag;

import com.wuming.api.rag.dto.RagDocument;
import com.wuming.novel.domain.entity.NovelPassage;
import com.wuming.novel.integration.rpc.rag.RagIndexService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NovelPassageVectorIndexServiceTest {

    private final RagIndexService ragIndexService = mock(RagIndexService.class);
    private final NovelPassageVectorIndexService service = new NovelPassageVectorIndexService(ragIndexService);

    @Test
    @SuppressWarnings("unchecked")
    void shouldConvertPassageToRagDocumentWithStableMetadata() {
        ReflectionTestUtils.setField(service, "passageIndexName", "novel_passage");
        NovelPassage passage = new NovelPassage();
        passage.setId(10L);
        passage.setNovelId(20L);
        passage.setChapterId(30L);
        passage.setSequence(4);
        passage.setChapterSequence(2);
        passage.setStartParagraph(5);
        passage.setEndParagraph(8);
        passage.setContent("片段内容");

        service.upsert(List.of(passage));

        ArgumentCaptor<List<RagDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(ragIndexService).upsertDocuments(org.mockito.ArgumentMatchers.eq("novel_passage"), captor.capture());
        RagDocument document = captor.getValue().get(0);
        assertThat(document.getDocumentId()).isEqualTo("10");
        assertThat(document.getContent()).isEqualTo("片段内容");
        assertThat(document.getMetadata())
                .containsEntry("novel_id", 20L)
                .containsEntry("chapter_id", 30L)
                .containsEntry("passage_id", 10L)
                .containsEntry("passage_sequence", 4)
                .containsEntry("chapter_sequence", 2)
                .containsEntry("start_paragraph", 5)
                .containsEntry("end_paragraph", 8);
    }
}
