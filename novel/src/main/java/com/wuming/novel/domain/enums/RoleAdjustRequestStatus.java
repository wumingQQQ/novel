package com.wuming.novel.domain.enums;

/**
 * 角色调整请求处理状态
 */
public enum RoleAdjustRequestStatus {
    PENDING,
    GENERATING,
    READY,      // 候选项生成完毕，等待用户评审
    CONFIRMED,  // 用户评审完毕后进入该状态
    COMPLETED,
    FAILED,
    CANCELLED
}
