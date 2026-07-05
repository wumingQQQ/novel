package com.wuming.rag.search;

import com.wuming.api.rag.dto.RagHitDto;
import com.wuming.api.rag.dto.RoleExampleSearchRequest;
import com.wuming.api.rag.dto.SearchResult;
import com.wuming.rag.config.RagServiceProperties;
import com.wuming.rag.rerank.RerankService;
import com.wuming.rag.vector.redis.RedisVectorIndexService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoleRuntimeSearchServiceTest {

    private final RedisVectorIndexService vectorIndexService = mock(RedisVectorIndexService.class);
    private final RerankService rerankService = mock(RerankService.class);
    private final RagServiceProperties properties = new RagServiceProperties();
    private final RoleRuntimeSearchService searchService = new RoleRuntimeSearchService(
            vectorIndexService,
            rerankService,
            properties
    );

    @Test
    void shouldSearchRoleExamplesWithCharacterFilterAndDefaultTopK() {
        RagHitDto hit = new RagHitDto();
        hit.setDocumentId("role_example:1");
        hit.setText("样本文本");
        properties.getRetrieve().setDefaultTopK(3);

        when(vectorIndexService.search(
                "idx:rag:role-example",
                "你好",
                Map.of("characterId", 1001L),
                3
        )).thenReturn(List.of(hit));

        RoleExampleSearchRequest request = new RoleExampleSearchRequest();
        request.setIndexName("idx:rag:role-example");
        request.setCharacterId(1001L);
        request.setQuery("你好");

        SearchResult result = searchService.searchRoleExamples(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getHits()).containsExactly(hit);
    }

    @Test
    void shouldRejectBlankIndexName() {
        RoleExampleSearchRequest request = new RoleExampleSearchRequest();
        request.setIndexName(" ");
        request.setCharacterId(1001L);
        request.setQuery("你好");

        assertThatThrownBy(() -> searchService.searchRoleExamples(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("indexName不能为空");
    }
}
