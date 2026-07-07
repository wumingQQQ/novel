package com.wuming.novel.integration.message.rolereactionruleindex;

import com.wuming.novel.integration.message.CompleteEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色反应规则向量索引事件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RoleReactionRuleIndexEvent extends CompleteEvent {
    /**
     * 角色主键。
     */
    private Long characterId;

    /**
     * 角色名称。
     */
    private String characterName;

    /**
     * 需要从向量库删除的旧规则主键。
     */
    private List<Long> deletedRuleIds = new ArrayList<>();

    /**
     * 需要写入向量库的新规则主键。
     */
    private List<Long> indexedRuleIds = new ArrayList<>();
}
