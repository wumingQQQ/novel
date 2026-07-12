package com.wuming.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.wuming.novel.domain.enums.RoleAdjustChangeType;
import com.wuming.novel.domain.enums.RoleAdjustStatus;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 一次用户角色调整请求中一个候选调整项
 */
@Data
@TableName("role_adjust_items")
public class RoleAdjustItem implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long requestId;   // 对应一个 RoleAdjustRequest
    private RoleAdjustChangeType changeType;  // 调整类型：新增、替换、移除

    // 对应role version中的BehaviorAdjustmentSnapshot的主键
    private String adjustmentId;
    private String targetAdjustmentId;

    private String applicability;  // 场景
    private String expectedBehavior;
    private String forbiddenBehavior;
    private RoleAdjustStatus status;          // 用户评审结果：拒绝、接受
    private String revisionFeedback;  // 用户要求改写时填写的意见
    private String revisionError;
    private Integer displayOrder;    // 在本次请求中的展示顺序
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
