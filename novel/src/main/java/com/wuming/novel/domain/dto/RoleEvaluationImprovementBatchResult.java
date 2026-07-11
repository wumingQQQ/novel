package com.wuming.novel.domain.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * LLM 对同一角色版本多条低分运行进行汇总后返回的有限规则改进结果。
 */
public record RoleEvaluationImprovementBatchResult(
        @JsonPropertyDescription("是否存在足够一致的证据支持规则改进") Boolean shouldImprove,
        @JsonPropertyDescription("对全部输入运行的简洁汇总结论") String summary,
        @JsonPropertyDescription("最多为系统指定数量的规则改进建议") List<Proposal> improvements
) {

    /**
     * 一条由多次评测运行共同支持的规则改进建议。
     */
    public record Proposal(
            @JsonPropertyDescription("最需要改进的现有反应规则主键") Long ruleId,
            @JsonPropertyDescription("改进后的简洁反应规则") String proposedRule,
            @JsonPropertyDescription("该规则如何解决重复出现的问题") String rationale,
            @JsonPropertyDescription("至少两个支撑该建议的评测运行主键") List<Long> evidenceRunIds
    ) {
    }
}
