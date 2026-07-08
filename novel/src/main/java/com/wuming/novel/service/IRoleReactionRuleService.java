package com.wuming.novel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wuming.novel.domain.entity.RoleReactionRule;

/**
 * 角色情境反应规则基础服务。
 */
public interface IRoleReactionRuleService extends IService<RoleReactionRule> {

    /**
     * 为指定角色构建情境反应规则，替换旧规则，并在事务提交后同步刷新规则向量索引。
     *
     * @param characterId 角色id
     * @return 本次保存的规则数量
     */
    int buildRules(Long characterId);
}
