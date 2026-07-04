package com.wuming.novel.role.dto;

import lombok.Data;

/**
 * Chat 运行时使用的角色原作样本。
 */
@Data
public class RoleExampleDto {

    /**
     * 样本类型：DIALOGUE、ACTION_DESCRIPTION、NARRATION_EVAL
     */
    private String sampleType;

    /**
     * 完整样本文本
     */
    private String sampleText;

    /**
     * 对话原文，仅 DIALOGUE 类型使用
     */
    private String dialogueText;

    /**
     * 动作/神态描写，仅 ACTION_DESCRIPTION 类型使用
     */
    private String actionDescription;

    /**
     * 旁白评价，仅 NARRATION_EVAL 类型使用
     */
    private String narrationEval;

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
