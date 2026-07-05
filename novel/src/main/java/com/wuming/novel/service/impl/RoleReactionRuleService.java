package com.wuming.novel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.domain.entity.RoleReactionRule;
import com.wuming.novel.infrastructure.mapper.RoleReactionRuleMapper;
import com.wuming.novel.service.IRoleReactionRuleService;
import org.springframework.stereotype.Service;

/**
 * 角色情境反应规则基础服务实现。
 */
@Service
public class RoleReactionRuleService
        extends ServiceImpl<RoleReactionRuleMapper, RoleReactionRule>
        implements IRoleReactionRuleService {
}
