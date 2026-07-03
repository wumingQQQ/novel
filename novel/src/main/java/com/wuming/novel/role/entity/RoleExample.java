package com.wuming.novel.role.entity;

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
 * 角色原作样本。
 *
 * 这是新角色构建流程的核心资产，用于 chat 运行时动态召回并作为
 * few-shot 示例注入提示词。
 */
@Data
@TableName("role_examples")
public class RoleExample implements Serializable {

    /**
     * 样本ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 角色ID
     */
    private Long characterId;

    /**
     * 角色名称，便于日志排查和人工查看
     */
    private String characterName;

    /**
     * 来源文本块ID
     */
    private Long passageId;

    /**
     * 样本类型：DIALOGUE、CHARACTER_DESCRIPTION
     */
    private String sampleType;

    /**
     * 完整样本文本，用于向量化和 prompt 注入
     */
    private String sampleText;

    /**
     * 角色台词，仅 DIALOGUE 类型使用
     */
    private String dialogueText;

    /**
     * 样本前文上下文
     */
    private String contextBefore;

    /**
     * 样本后文上下文
     */
    private String contextAfter;

    /**
     * 归因置信度，取值建议为0.0到1.0
     */
    private Double confidence;

    /**
     * 提取方式：RULE、LLM、RULE_LLM
     */
    private String extractMethod;

    /**
     * 情绪标签，初版可为空
     */
    private String emotionalTag;

    /**
     * 场景标签，初版可为空
     */
    private String situationTag;

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

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Serial
    private static final long serialVersionUID = 1L;
}
