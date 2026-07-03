package com.wuming.novel.role.dto;

import lombok.Data;

/**
 * Chat 运行时使用的角色原作样本。
 */
@Data
public class RoleExampleDto {

    /**
     * 样本类型：DIALOGUE、CHARACTER_DESCRIPTION
     */
    private String sampleType;

    /**
     * 完整样本文本
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
     * 归因置信度
     */
    private Double confidence;
}
