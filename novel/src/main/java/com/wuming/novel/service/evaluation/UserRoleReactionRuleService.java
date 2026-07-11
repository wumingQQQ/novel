package com.wuming.novel.service.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.novel.domain.entity.RoleReactionRule;
import com.wuming.novel.domain.entity.UserRoleReactionRule;
import com.wuming.novel.domain.entity.UserRoleVersion;
import com.wuming.novel.infrastructure.mapper.RoleReactionRuleMapper;
import com.wuming.novel.infrastructure.mapper.UserRoleReactionRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理用户角色版本的完整反应规则快照。
 */
@Service
@RequiredArgsConstructor
public class UserRoleReactionRuleService {
    private final RoleReactionRuleMapper publicRuleMapper;
    private final UserRoleReactionRuleMapper userRuleMapper;

    /** 读取评测提示词所需的有效规则，最多保留五条。 */
    public List<RoleReactionRule> loadEffectiveRules(Long characterId, Long versionId) {
        if (versionId == null) return listPublicRules(characterId).stream().limit(5).toList();
        List<UserRoleReactionRule> rules = userRuleMapper.selectList(new LambdaQueryWrapper<UserRoleReactionRule>()
                .eq(UserRoleReactionRule::getUserRoleVersionId, versionId).orderByAsc(UserRoleReactionRule::getId));
        if (rules.isEmpty()) throw new IllegalStateException("个人角色版本缺少反应规则快照");
        return rules.stream().map(this::toRuleView).limit(5).toList();
    }

    /** 将公共规则或历史规则复制为新版本的完整快照。 */
    public void copySnapshot(Long characterId, UserRoleVersion baseVersion, Long targetVersionId) {
        if (baseVersion == null) {
            listPublicRules(characterId).forEach(rule -> insert(targetVersionId, rule.getId(), rule.getSituation(), rule.getRule()));
            return;
        }
        List<UserRoleReactionRule> source = userRuleMapper.selectList(new LambdaQueryWrapper<UserRoleReactionRule>()
                .eq(UserRoleReactionRule::getUserRoleVersionId, baseVersion.getId()));
        if (source.isEmpty()) throw new IllegalStateException("历史个人版本缺少反应规则快照");
        source.forEach(rule -> insert(targetVersionId, rule.getSourceRuleId(), rule.getSituation(), rule.getRule()));
    }

    /** 在新版本快照中替换指定来源公共规则，并返回该快照规则。 */
    public UserRoleReactionRule applyImprovement(Long versionId, RoleReactionRule publicRule, String proposedRule) {
        UserRoleReactionRule rule = userRuleMapper.selectOne(new LambdaQueryWrapper<UserRoleReactionRule>()
                .eq(UserRoleReactionRule::getUserRoleVersionId, versionId)
                .eq(UserRoleReactionRule::getSourceRuleId, publicRule.getId()));
        if (rule == null) throw new IllegalStateException("新个人版本缺少待改进规则快照");
        rule.setSituation(publicRule.getSituation());
        rule.setRule(proposedRule);
        userRuleMapper.updateById(rule);
        return rule;
    }

    /**
     * 在新版本快照中依次应用多条来源公共规则不同的改进，并返回更新后的个人规则。
     *
     * @param versionId 新建个人角色版本主键
     * @param improvements 待应用的规则与新规则文本
     * @return 已更新的个人规则列表
     */
    public List<UserRoleReactionRule> applyImprovements(Long versionId,
                                                         List<RuleChange> improvements) {
        return improvements.stream()
                .map(item -> applyImprovement(versionId, item.publicRule(), item.proposedRule()))
                .toList();
    }

    /** 查询角色完整公共规则，用于创建版本快照。 */
    private List<RoleReactionRule> listPublicRules(Long characterId) {
        return publicRuleMapper.selectList(new LambdaQueryWrapper<RoleReactionRule>()
                .eq(RoleReactionRule::getCharacterId, characterId).orderByAsc(RoleReactionRule::getId));
    }

    private RoleReactionRule toRuleView(UserRoleReactionRule snapshot) {
        RoleReactionRule rule = new RoleReactionRule();
        rule.setId(snapshot.getSourceRuleId());
        rule.setSituation(snapshot.getSituation());
        rule.setRule(snapshot.getRule());
        return rule;
    }

    private void insert(Long versionId, Long sourceRuleId, String situation, String ruleText) {
        UserRoleReactionRule snapshot = new UserRoleReactionRule();
        snapshot.setUserRoleVersionId(versionId);
        snapshot.setSourceRuleId(sourceRuleId);
        snapshot.setSituation(situation);
        snapshot.setRule(ruleText);
        userRuleMapper.insert(snapshot);
    }

    /**
     * 一条待写入个人版本快照的规则修改。
     *
     * @param publicRule 来源公共规则
     * @param proposedRule 用户确认的新规则文本
     */
    public record RuleChange(RoleReactionRule publicRule, String proposedRule) {
    }
}
