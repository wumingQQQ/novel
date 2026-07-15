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
     * 召回角色样本和反应规则，并拼接为提示词上下文。
     *
     * @param characterId 角色id
     * @param query 用户输入
     * @return 可注入聊天提示词的上下文
     */
    public String buildContextPrompt(Long characterId, String query) {
        if (characterId == null || query == null || query.isBlank()) {
            return "";
        }

        long start = System.currentTimeMillis();
        List<SearchHit> examples = retrieveRoleExamples(characterId, query);
        List<SearchHit> rules = retrieveReactionRules(characterId, query);
        log.info("角色运行时RAG召回完成，characterId: {}, exampleCount: {}, ruleCount: {}, costMs: {}",
                characterId, examples.size(), rules.size(), System.currentTimeMillis() - start);
        if (examples.isEmpty() && rules.isEmpty()) {
            return "";
        }
        return formatPrompt(examples, rules);
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

    private String formatPrompt(List<SearchHit> examples, List<SearchHit> rules) {
        StringBuilder builder = new StringBuilder();
        builder.append("【当前可参考的原作资产】\n");
        builder.append("以下内容只用于学习角色反应方式和说话节奏，不要复述来源，不要提到检索、样本或规则。\n");
        if (!rules.isEmpty()) {
            builder.append("\n情境反应规则：\n");
            for (int i = 0; i < rules.size(); i++) {
                builder.append(i + 1).append(". ").append(rules.get(i).getContent()).append('\n');
            }
        }
        if (!examples.isEmpty()) {
            builder.append("\n原作互动样本：\n");
            for (int i = 0; i < examples.size(); i++) {
                builder.append(i + 1).append(". ").append(examples.get(i).getContent()).append('\n');
            }
        }
        return builder.toString();
    }
}
