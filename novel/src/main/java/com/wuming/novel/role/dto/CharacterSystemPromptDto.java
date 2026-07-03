package com.wuming.novel.role.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat system prompt 所需的角色轻量约束。
 */
@Data
public class CharacterSystemPromptDto {

    /**
     * 角色ID
     */
    private Long characterId;

    /**
     * 小说名称
     */
    private String novelName;

    /**
     * 角色名称
     */
    private String characterName;

    /**
     * 核心性格特质
     */
    private String coreTraits;

    /**
     * 说话风格描述
     */
    private String speakingStyle;

    /**
     * 角色绝不应做的行为
     */
    private String forbiddenBehaviors;

    /**
     * 代表性原作样本
     */
    private List<RoleExampleDto> representativeExamples = new ArrayList<>();
}
