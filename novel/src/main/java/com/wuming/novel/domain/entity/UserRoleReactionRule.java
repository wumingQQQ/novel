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
 * 用户个人角色版本中的反应规则覆写或新增规则。
 */
@Data
@TableName("user_role_reaction_rules")
public class UserRoleReactionRule implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属个人角色版本。 */
    private Long userRoleVersionId;

    /** 被覆写的公共规则；用户新增规则时为空。 */
    private Long sourceRuleId;

    private String situation;

    private String rule;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Serial
    private static final long serialVersionUID = 1L;
}
