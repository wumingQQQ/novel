package com.wuming.novel.domain.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 角色样本检索参数。
 *
 * MVP 只使用 characterId、currentInput 和 limit，其余字段作为后续扩展位。
 */
@Data
public class RoleExampleRetrievalQuery {

    /**
     * 角色ID
     */
    private Long characterId;

    /**
     * 用户当前输入
     */
    private String currentInput;

    /**
     * 最近对话内容，初版可为空
     */
    private List<String> recentMessages;

    /**
     * 用户与角色关系阶段，初版不使用
     */
    private String intimacyStage;

    /**
     * 扩展元数据
     */
    private Map<String, Object> metadata;

    /**
     * 召回数量
     */
    private int limit = 3;
}
