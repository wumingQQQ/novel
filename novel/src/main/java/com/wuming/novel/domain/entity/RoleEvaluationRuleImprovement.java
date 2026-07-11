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
import java.util.List;

/**
 * 根据角色评测结果生成、等待人工审核的反应规则改进建议。
 */
@Data
@TableName("role_evaluation_rule_improvements")
public class RoleEvaluationRuleImprovement implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long evaluationId;
    /** 所属汇总改进批次；旧数据为空。 */
    private Long batchId;
    /** 批次内第一条支撑运行，用于兼容旧审计字段。完整证据见关联表。 */
    private Long runId;
    /** 查询批次建议时填充的全部支撑运行主键，不持久化到本表。 */
    @TableField(exist = false)
    private List<Long> evidenceRunIds;
    private Long characterId;
    private Long ruleId;
    private String situation;
    private String originalRule;
    private String proposedRule;
    private String rationale;
    private String status;
    /** 生成新个人版本时选定的历史基线版本；公共基线时为空。 */
    private Long baseUserRoleVersionId;
    private Long userRoleVersionId;
    private Long userRoleReactionRuleId;
    private LocalDateTime reviewedTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Serial
    private static final long serialVersionUID = 1L;
}
