package com.wuming.chat.rag.role;

import com.wuming.api.rag.RagFacade;
import com.wuming.api.rag.dto.SearchHit;
import com.wuming.api.rag.dto.spec.ReactionRuleSearchRequest;
import com.wuming.api.rag.dto.spec.RoleExampleSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 角色运行时RAG召回服务。
 */
@Slf4j
@Service
public class RoleRuntimeRagService {

    @DubboReference(
            url = "${chat.rag.url:tri://127.0.0.1:50053}",
            timeout = 60000,
            retries = 0,
            mock = "com.wuming.api.rag.RagFacadeMock"
    )
    private RagFacade ragFacade;

    @Value("${chat.rag.role-example-index-name:role_example}")
    private String roleExampleIndexName;

    @Value("${chat.rag.reaction-rule-index-name:reaction_rule}")
    private String reactionRuleIndexName;

    @Value("${chat.rag.role-example-top-k:10}")
    private int roleExampleTopK;

    @Value("${chat.rag.role-example-top-n:3}")
    private int roleExampleTopN;

    @Value("${chat.rag.reaction-rule-top-k:8}")
    private int reactionRuleTopK;

    @Value("${chat.rag.reaction-rule-top-n:2}")
    private int reactionRuleTopN;

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
        List<SearchHit> examples = searchRoleExamples(characterId, query);
        List<SearchHit> rules = searchReactionRules(characterId, query);
        log.info("角色运行时RAG召回完成，characterId: {}, exampleCount: {}, ruleCount: {}, costMs: {}",
                characterId, examples.size(), rules.size(), System.currentTimeMillis() - start);
        if (examples.isEmpty() && rules.isEmpty()) {
            return "";
        }
        return formatPrompt(examples, rules);
    }

    private List<SearchHit> searchRoleExamples(Long characterId, String query) {
        RoleExampleSearchRequest request = new RoleExampleSearchRequest();
        request.setIndexName(roleExampleIndexName);
        request.setCharacterId(characterId);
        request.setQuery(query);
        request.setTopK(Math.max(1, roleExampleTopK));
        request.setTopN(Math.max(1, roleExampleTopN));
        request.setRerank(true);
        return safeSearchRoleExamples(request);
    }

    private List<SearchHit> searchReactionRules(Long characterId, String query) {
        ReactionRuleSearchRequest request = new ReactionRuleSearchRequest();
        request.setIndexName(reactionRuleIndexName);
        request.setCharacterId(characterId);
        request.setQuery(query);
        request.setTopK(Math.max(1, reactionRuleTopK));
        request.setTopN(Math.max(1, reactionRuleTopN));
        request.setRerank(true);
        return safeSearchReactionRules(request);
    }

    private List<SearchHit> safeSearchRoleExamples(RoleExampleSearchRequest request) {
        try {
            List<SearchHit> hits = ragFacade.searchRoleExamples(request);
            return hits == null ? List.of() : hits;
        } catch (RuntimeException e) {
            log.warn("角色样本召回失败，characterId: {}", request.getCharacterId(), e);
            return List.of();
        }
    }

    private List<SearchHit> safeSearchReactionRules(ReactionRuleSearchRequest request) {
        try {
            List<SearchHit> hits = ragFacade.searchReactionRules(request);
            return hits == null ? List.of() : hits;
        } catch (RuntimeException e) {
            log.warn("角色反应规则召回失败，characterId: {}", request.getCharacterId(), e);
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
