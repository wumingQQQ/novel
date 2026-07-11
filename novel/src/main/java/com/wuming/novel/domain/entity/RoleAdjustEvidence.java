package com.wuming.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 候选调整项引用的原作证据，写入后不可修改
 */
@Data
@TableName("role_adjust_evidences")
public class RoleAdjustEvidence implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long itemId;
    private Long passageId;
    private String quoteText;
    private String relevanceExplanation;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
