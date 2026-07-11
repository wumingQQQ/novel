package com.wuming.novel.domain.enums;

/**
 * 具体改进意见的状态
 */
public enum RoleAdjustStatus {
    PENDING,    // 调整意见已生成，待用户评审
    ACCEPTED,
    REJECTED,
    REVISING;       // 用户提交了改写意见，需要等待后续重新生成
}
