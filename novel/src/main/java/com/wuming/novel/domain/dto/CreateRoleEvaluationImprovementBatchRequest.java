package com.wuming.novel.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 选择多条低分运行并创建规则改进批次的请求。
 */
@Data
public class CreateRoleEvaluationImprovementBatchRequest {

    /** 同一角色版本下、用于汇总分析的低分运行主键列表。 */
    @NotEmpty
    @Size(min = 2, max = 12)
    private List<Long> runIds;
}
