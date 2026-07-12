package com.wuming.novel.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色调整候选项修订结果。
 */
@Data
public class ReviseRoleAdjustResult {
    private Long requestId;
    private List<Long> revisedItemIds = new ArrayList<>();
    private List<ItemError> itemErrors = new ArrayList<>();

    /**
     * 记录单个候选项修订失败原因，供前端继续展示给用户。
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
