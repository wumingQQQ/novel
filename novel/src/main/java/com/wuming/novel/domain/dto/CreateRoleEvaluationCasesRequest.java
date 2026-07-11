package com.wuming.novel.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 批量构造角色评测案例的请求。
 */
@Data
public class CreateRoleEvaluationCasesRequest {
    @NotBlank
    private String datasetVersion;
    @NotNull
    private Integer limit;
}
