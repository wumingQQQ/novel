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
 * 单个角色评测案例的一次不可变运行记录。
 */
@Data
@TableName("role_evaluation_runs")
public class RoleEvaluationRun implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long evaluationId;
    private Long caseId;
    /** 本次运行实际使用的个人版本快照；公共基线运行时为空。 */
    private Long userRoleVersionId;
    private String status;
    private String configSnapshot;
    private String retrievedDocuments;
    private String responseContent;
    private Long generationCostMs;
    private String judgeResult;
    private Double totalScore;
    private String judgeReason;
    private String failureReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Serial
    private static final long serialVersionUID = 1L;
}
