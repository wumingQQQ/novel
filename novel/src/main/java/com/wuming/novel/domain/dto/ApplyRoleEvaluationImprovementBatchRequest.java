package com.wuming.novel.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 审核并应用一个规则改进批次的请求。
 */
@Data
public class ApplyRoleEvaluationImprovementBatchRequest {

    /** 用户选定的历史个人版本主键；为空时采用批次评测的当前版本。 */
    private Long baseUserRoleVersionId;

    /** 用户确认应用的建议主键，数量不得超过批次上限。 */
    @NotEmpty
    @Size(max = 2)
    private List<Long> improvementIds;
}
