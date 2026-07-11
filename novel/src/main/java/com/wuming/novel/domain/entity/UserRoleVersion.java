package com.wuming.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户个人角色演进轨迹中的一个不可变版本快照。
 */
@Data
@TableName("user_role_versions")
public class UserRoleVersion implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属用户个人角色演进轨迹。 */
    private Long userRoleTrackId;

    /** 轨迹内单调递增的版本号。 */
    private Integer versionNo;

    /** 本版本派生自的个人版本；从公共角色基线首次创建时为空。 */
    private Long parentVersionId;

    /** 触发本版本创建的评测主键。 */
    private Long sourceEvaluationId;

    /** 触发本版本创建的规则建议主键。 */
    private Long sourceImprovementId;

    /** 触发本版本创建的规则改进批次主键。 */
    private Long sourceImprovementBatchId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Serial
    private static final long serialVersionUID = 1L;
}
