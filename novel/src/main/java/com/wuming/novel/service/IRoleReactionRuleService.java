package com.wuming.novel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wuming.novel.domain.entity.RoleReactionRule;

import java.util.List;

/**
 * 角色情境反应规则基础服务。
 */
public interface IRoleReactionRuleService extends IService<RoleReactionRule> {

    /**
     * 查询预定义情境任务标识。
     *
     * @param characterId 角色id
     * @return 情境任务标识列表
     */
    List<String> situationKeys(Long characterId);

    /**
     * 为指定角色构建单个情境反应规则。
     *
     * @param characterId 角色id
     * @param situationKey 情境任务标识
     * @return 本次保存的规则数量
     */
    int buildRule(Long characterId, String situationKey);

    /**
     * 标记角色反应规则阶段已完成，后续等待画像构建。
     *
     * @param characterId 角色id
     */
    void completeRuleBuild(Long characterId);
}
