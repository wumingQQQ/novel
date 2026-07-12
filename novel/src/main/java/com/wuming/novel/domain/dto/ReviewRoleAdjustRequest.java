package com.wuming.novel.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 一次角色调整请求下全部候选项的用户评审结果。
 */
@Data
public class ReviewRoleAdjustRequest {
    @Valid
    @NotEmpty
    private List<ReviewRoleAdjustItemRequest> items;
}
