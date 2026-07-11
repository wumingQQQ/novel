package com.wuming.novel.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建用户独立角色评测的请求。
 */
@Data
public class CreateRoleEvaluationRequest {

    /** 用户选择参与评测的公共角色主键。 */
    @NotNull
    private Long characterId;
}
