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
 * 角色情境反应规则。
 */
@Data
@TableName(value = "role_reaction_rules", autoResultMap = true)
public class RoleReactionRule implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long characterId;

    private String characterName;

    /**
     * 情境描述
     */
    private String situation;

    /**
     * 归纳出的反应规则
     */
    private String rule;

    /**
     * 支撑证据的 passageId 列表
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> evidencePassageIds = new ArrayList<>();

    /**
     * 向量索引状态：PENDING、INDEXED、FAILED
     */
    private String vectorStatus;

    /**
     * 向量索引失败原因
     */
    private String vectorError;

    /**
     * 向量索引完成时间
     */
    private LocalDateTime indexedTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Serial
    private static final long serialVersionUID = 1L;
}
