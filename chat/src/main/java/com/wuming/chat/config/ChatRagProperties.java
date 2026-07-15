package com.wuming.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 角色运行时RAG召回的索引名与 topK/topN 配置。
 *
 * <p>Dubbo 引用地址 chat.rag.url 由 {@code @DubboReference} 注解直接解析占位符，不在此绑定。</p>
 */
@Data
@ConfigurationProperties(prefix = "chat.rag")
public class ChatRagProperties {
    private String roleExampleIndexName = "role_example";
    private String reactionRuleIndexName = "reaction_rule";
    private int roleExampleTopK = 10;
    private int roleExampleTopN = 3;
    private int reactionRuleTopK = 8;
    private int reactionRuleTopN = 2;

    /** 返回至少召回一条的角色样本 topK。 */
    public int roleExampleTopK() {
        return Math.max(1, roleExampleTopK);
    }

    /** 返回至少保留一条的角色样本 topN。 */
    public int roleExampleTopN() {
        return Math.max(1, roleExampleTopN);
    }

    /** 返回至少召回一条的反应规则 topK。 */
    public int reactionRuleTopK() {
        return Math.max(1, reactionRuleTopK);
    }

    /** 返回至少保留一条的反应规则 topN。 */
    public int reactionRuleTopN() {
        return Math.max(1, reactionRuleTopN);
    }
}
