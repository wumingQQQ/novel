package com.wuming.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 候选调整项引用的原作证据，写入后不可修改
 */
@Data
@TableName(value = "role_adjust_evidences", autoResultMap = true)
public class RoleAdjustEvidence implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long itemId;

    /**
     * 支撑该候选调整项的原作 Passage 主键列表。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> passageIds;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
