package com.wuming.chat.rag.role;

import com.wuming.api.rag.RagFacade;
import com.wuming.api.rag.dto.SearchHit;
import com.wuming.api.rag.dto.spec.SearchFilter;
import com.wuming.api.rag.dto.spec.SearchRequest;
import com.wuming.chat.config.ChatRagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 角色运行时RAG召回服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleRuntimeRagService {

    private final ChatRagProperties ragProperties;

    @DubboReference(
            url = "${chat.rag.url:tri://127.0.0.1:50053}",
            timeout = 60000,
            retries = 0,
            mock = "com.wuming.api.rag.RagFacadeMock"
    )
    private RagFacade ragFacade;

    /**
     * 召回本轮角色回复需要的反应规则和原作样本。
     *
     * @param characterId 角色主键
     * @param query 本轮用户输入
     * @return 结构化角色参考材料
     */
    public RoleRetrievalSnapshot retrieve(Long characterId, String query) {
        if (characterId == null || query == null || query.isBlank()) {
            return new RoleRetrievalSnapshot(List.of(), List.of());
        }

        long start = System.currentTimeMillis();
        List<SearchHit> examples = retrieveRoleExamples(characterId, query);
        List<SearchHit> rules = retrieveReactionRules(characterId, query);

        log.info(
                "角色运行时RAG召回完成，characterId: {}, exampleCount: {}, ruleCount: {}, costMs: {}",
                characterId,
                examples.size(),
                rules.size(),
                System.currentTimeMillis() - start
        );

        return new RoleRetrievalSnapshot(rules, examples);
    }

    private List<SearchHit> retrieveRoleExamples(Long characterId, String query) {
        SearchRequest request = new SearchRequest();
        request.setIndexName(ragProperties.getRoleExampleIndexName());
        request.setQuery(query);
        request.setMultiQuery(true);
        request.setFilters(List.of(SearchFilter.eq("character_id", characterId)));
        request.setTopK(ragProperties.roleExampleTopK());
        request.setTopN(ragProperties.roleExampleTopN());
        return safeSearch(request, "角色样本", characterId);
    }

    private List<SearchHit> retrieveReactionRules(Long characterId, String query) {
        SearchRequest request = new SearchRequest();
        request.setIndexName(ragProperties.getReactionRuleIndexName());
        request.setQuery(query);
        request.setMultiQuery(true);
        request.setFilters(List.of(SearchFilter.eq("character_id", characterId)));
        request.setTopK(ragProperties.reactionRuleTopK());
        request.setTopN(ragProperties.reactionRuleTopN());
        return safeSearch(request, "角色反应规则", characterId);
    }

    private List<SearchHit> safeSearch(SearchRequest request, String scene, Long characterId) {
        try {
            List<SearchHit> hits = ragFacade.search(request);
            return hits == null ? List.of() : hits;
        } catch (RuntimeException e) {
            log.warn("{}召回失败，characterId: {}, indexName: {}", scene, characterId, request.getIndexName(), e);
            return List.of();
        }
    }

}
