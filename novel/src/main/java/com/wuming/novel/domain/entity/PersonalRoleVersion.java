package com.wuming.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 不可变个人角色版本，只保存调整补丁
 */
@Data
@TableName(value = "personal_role_versions", autoResultMap = true)
public class PersonalRoleVersion implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long trackId;
    private Integer versionNo;
    private Long parentVersionId;     // 基于哪个版本角色创建的，为null表示基于公共角色
    private Long sourceRequestId;     // 来自哪次请求创建的
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<BehaviorAdjustmentSnapshot> behaviorAdjustmentsSnapshot;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Data
    public static class BehaviorAdjustmentSnapshot implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String adjustmentId;
        private Long sourceAdjustItemId;
        private String applicability;
        private String expectedBehavior;
        private String forbiddenBehavior;
        private Integer displayOrder;
    }
}

