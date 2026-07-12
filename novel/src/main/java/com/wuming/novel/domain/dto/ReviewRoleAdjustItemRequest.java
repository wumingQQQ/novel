package com.wuming.novel.domain.dto;

import com.wuming.novel.domain.enums.RoleAdjustStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 单个角色调整候选项的用户评审结果。
 */
@Data
public class ReviewRoleAdjustItemRequest {
    @NotNull
    private Long itemId;
    @NotNull
    private RoleAdjustStatus status;
    private String revisionFeedback;
}
