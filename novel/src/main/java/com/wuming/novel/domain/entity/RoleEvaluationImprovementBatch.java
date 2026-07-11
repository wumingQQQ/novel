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
 * 汇总同一角色版本多条低分运行后生成的有限规则改进批次。
 */
@Data
@TableName("role_evaluation_improvement_batches")
public class RoleEvaluationImprovementBatch implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long evaluationId;

    /** 批次中所有运行实际使用的个人角色版本；公共基线时为空。 */
    private Long userRoleVersionId;

    /** 输入 LLM 汇总的运行数量。 */
    private Integer runCount;

    /** 本批次最多允许输出并应用的规则修改数量。 */
    private Integer maxChanges;

    /** LLM 对整组运行的汇总说明；没有稳定问题时也会记录。 */
    private String summary;

    /** DRAFT/APPLIED/REJECTED。 */
    private String status;

    private LocalDateTime reviewedTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Serial
    private static final long serialVersionUID = 1L;
}
