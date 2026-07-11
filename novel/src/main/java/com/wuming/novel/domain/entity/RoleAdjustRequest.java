package com.wuming.novel.domain.entity;


import com.baomidou.mybatisplus.annotation.*;
import com.wuming.novel.domain.enums.RoleAdjustRequestStatus;
import com.wuming.novel.domain.enums.RoleAdjustStatus;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户提交的个人角色调整请求
 */
@Data
@TableName("role_adjust_requests")
public class RoleAdjustRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long characterId;
    private Long baseVersionId;   // 作为基线的角色版本，为null代表为公共角色
    private String requirement;   // 用户需求
    private String chatText;      // 聊天上下文，辅助理解用户意图
    private RoleAdjustRequestStatus status;        // 请求执行状态
    private String failureReason;
    private Long createdVersionId;  // 依据该请求创建的角色版本
    private LocalDateTime cancelledTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
