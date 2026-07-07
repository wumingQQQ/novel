package com.wuming.novel.integration.message.roleexampleindex;

import com.wuming.novel.integration.message.CompleteEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色样本向量索引事件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RoleExampleIndexEvent extends CompleteEvent {
    /**
     * 角色主键。
     */
    private Long characterId;

    /**
     * 角色名称。
     */
    private String characterName;

    /**
     * 需要从向量库删除的旧样本主键。
     */
    private List<Long> deletedExampleIds = new ArrayList<>();

    /**
     * 需要写入向量库的新样本主键。
     */
    private List<Long> indexedExampleIds = new ArrayList<>();
}
