package com.wuming.novel.role.entity;

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
import java.util.ArrayList;
import java.util.List;

/**
 * 角色轻量画像摘要。
 *
 * 该表只保存 chat system prompt 所需的稳定约束，角色表现主要依赖
 * RoleExample 动态召回。
 */
@Data
@TableName(value = "role_profiles", autoResultMap = true)
public class RoleProfile implements Serializable {

    /**
     * 摘要ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 角色ID
     */
    private Long characterId;

    /**
     * 角色名称
     */
    private String characterName;

    /**
     * 小说ID
     */
    private Long novelId;

    /**
     * 小说名称
     */
    private String novelName;

    /**
     * 基础信息
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private BasicInfo basicInfo = new BasicInfo();

    /**
     * 核心性格特质
     */
    private String coreTraits;

    /**
     * 说话风格描述
     */
    private String speakingStyle;

    /**
     * 角色绝不应做的行为
     */
    private String forbiddenBehaviors;

    /**
     * 关键关系
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<KeyRelationship> keyRelationships = new ArrayList<>();

    /**
     * 代表性样本ID
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> representativeExamples = new ArrayList<>();

    /**
     * 构建版本
     */
    private String buildVersion;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 角色基础信息。
     */
    @Data
    public static class BasicInfo implements Serializable {

        /**
         * 年龄或年龄段，没有明确证据时为空
         */
        private String age;

        /**
         * 性别，没有明确证据时为空
         */
        private String gender;

        /**
         * 职业或身份
         */
        private String occupation;

        /**
         * 外貌或关键形象描述
         */
        private String appearance;

        @Serial
        private static final long serialVersionUID = 1L;
    }

    /**
     * 角色关键关系。
     */
    @Data
    public static class KeyRelationship implements Serializable {

        /**
         * 相关角色名称
         */
        private String name;

        /**
         * 关系类型
         */
        private String relation;

        /**
         * 目标角色对该对象的态度
         */
        private String attitude;

        @Serial
        private static final long serialVersionUID = 1L;
    }
}
