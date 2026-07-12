package com.wuming.novel.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色调整候选项评审提交结果。
 */
@Data
public class ReviewRoleAdjustResult {
    private Long requestId;
    private boolean confirmed;
    private List<Long> reviewedItemIds = new ArrayList<>();
    private List<ItemError> itemErrors = new ArrayList<>();

    /**
     * 记录单个候选项未处理的原因，供前端继续展示并提示用户修正。
     */
    @Data
    public static class ItemError {
        private Long itemId;
        private String message;

        public ItemError(Long itemId, String message) {
            this.itemId = itemId;
            this.message = message;
        }
    }
}
