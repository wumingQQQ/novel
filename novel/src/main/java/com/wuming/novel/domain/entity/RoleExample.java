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
 * 角色原作样本。
 *
 * 这是新角色构建流程的核心资产，用于 chat 运行时动态召回并作为
 * few-shot 示例注入提示词。
 */
@Data
@TableName("role_examples")
public class RoleExample implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long characterId;

    /**
     * 角色名称，便于日志排查和人工查看
     */
    private String characterName;

    private Long passageId;

    /**
     * 样本类型：INTERACTION、NARRATION_EVAL
     */
    private String sampleType;

    /**
     * 完整样本文本，用于向量化和 prompt 注入
     */
    private String sampleText;

    /**
     * 归因置信度，取值建议为0.0到1.0
     */
    private Double confidence;

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
