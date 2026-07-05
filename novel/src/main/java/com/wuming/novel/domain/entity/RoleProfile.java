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

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long characterId;

    private String characterName;

    private Long novelId;

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
     * 说话风格
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private SpeakingStyle speakingStyle = new SpeakingStyle();

    /**
     * 角色绝不应做的行为
     */
    private String forbiddenBehaviors;

    /**
     * 构建版本
     */
    private String buildVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

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
     * 角色说话风格。
     */
    @Data
    public static class SpeakingStyle implements Serializable {

        /**
         * 一句话概括核心风格
         */
        private String signature;

        /**
         * 有辨识度的具体句式或表达模式
         */
        private List<String> distinctivePatterns = new ArrayList<>();

        /**
         * 明确不会出现的表达模式
         */
        private List<String> avoidPatterns = new ArrayList<>();

        @Serial
        private static final long serialVersionUID = 1L;
    }
}
