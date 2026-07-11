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
 * 一条规则改进建议与其支撑评测运行之间的证据关联。
 */
@Data
@TableName("role_evaluation_rule_improvement_runs")
public class RoleEvaluationRuleImprovementRun implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long improvementId;
    private Long runId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Serial
    private static final long serialVersionUID = 1L;
}
