package com.wuming.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户个人角色版本的完整画像快照。
 */
@Data
@TableName(value = "user_role_profiles", autoResultMap = true)
public class UserRoleProfile implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userRoleVersionId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private RoleProfile.BasicInfo basicInfo;

    private String coreTraits;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private RoleProfile.SpeakingStyle speakingStyle;

    private String forbiddenBehaviors;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Serial
    private static final long serialVersionUID = 1L;
}
