package com.wuming.novel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wuming.novel.domain.entity.RoleReactionRule;

import java.util.List;

/**
 * 角色情境反应规则基础服务。
 */
public interface IRoleReactionRuleService extends IService<RoleReactionRule> {

    /**
     * 为指定角色构建情境反应规则。
     *
     * @param characterId 角色id
     * @return 本次保存的规则数量
     */
    int buildRules(Long characterId);


}
