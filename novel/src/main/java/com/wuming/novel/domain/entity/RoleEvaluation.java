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
 * 用户针对公共角色创建的独立评测。
 */
@Data
@TableName("role_evaluations")
public class RoleEvaluation implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 评测创建者，也是所有评测数据的权限主体。 */
    private Long userId;

    /** 被评测的公共角色。 */
    private Long characterId;

    /** 用户针对该公共角色的唯一演进轨迹；首次调整前为空。 */
    private Long userRoleTrackId;

    /** 首次应用个人调整后绑定的个人角色版本；基线评测阶段为空。 */
    private Long userRoleVersionId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Serial
    private static final long serialVersionUID = 1L;
}
