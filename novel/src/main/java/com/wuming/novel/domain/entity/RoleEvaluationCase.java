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
 * 基于原作Passage构造的角色效果评测案例。
 */
@Data
@TableName("role_evaluation_cases")
public class RoleEvaluationCase implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long evaluationId;
    private String datasetVersion;
    private Long characterId;
    private Long passageId;
    private String sourceExampleIds;
    private String testInput;
    private String sourcePassage;
    private String expectedBehaviors;
    private String scoringRubric;
    private String difficulty;
    private String status;
    private LocalDateTime reviewedTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Serial
    private static final long serialVersionUID = 1L;
}
