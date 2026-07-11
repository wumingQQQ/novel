package com.wuming.novel.service.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.novel.domain.entity.RoleEvaluation;
import com.wuming.novel.domain.entity.RoleEvaluationRuleImprovement;
import com.wuming.novel.domain.entity.RoleEvaluationImprovementBatch;
import com.wuming.novel.domain.entity.RoleReactionRule;
import com.wuming.novel.domain.entity.UserRoleReactionRule;
import com.wuming.novel.domain.entity.UserRoleTrack;
import com.wuming.novel.domain.entity.UserRoleVersion;
import com.wuming.novel.infrastructure.mapper.UserRoleTrackMapper;
import com.wuming.novel.infrastructure.mapper.UserRoleVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 管理用户角色演进轨迹及不可变版本的创建。
 */
@Service
@RequiredArgsConstructor
public class UserRoleVersionService {
    private final UserRoleTrackMapper trackMapper;
    private final UserRoleVersionMapper versionMapper;
    private final UserRoleProfileService profileService;
    private final UserRoleReactionRuleService ruleService;

    /** 查询用户对公共角色已有的演进轨迹。 */
    public UserRoleTrack findTrack(Long userId, Long characterId) {
        return trackMapper.selectOne(new LambdaQueryWrapper<UserRoleTrack>()
                .eq(UserRoleTrack::getUserId, userId).eq(UserRoleTrack::getCharacterId, characterId));
    }

    /** 查询轨迹下的版本历史。 */
    public List<UserRoleVersion> listVersions(Long trackId) {
        return versionMapper.selectList(new LambdaQueryWrapper<UserRoleVersion>()
                .eq(UserRoleVersion::getUserRoleTrackId, trackId).orderByDesc(UserRoleVersion::getVersionNo));
    }

    /**
     * 从公共基线或指定历史版本创建新快照，并在其中应用一条规则建议。
     */
    @Transactional
    public VersionApplication applyImprovement(RoleEvaluation evaluation,
                                               RoleEvaluationRuleImprovement improvement,
                                               RoleReactionRule publicRule,
                                               Long requestedBaseVersionId) {
        UserRoleTrack track = getOrCreateTrack(evaluation);
        Long baseId = requestedBaseVersionId == null ? evaluation.getUserRoleVersionId() : requestedBaseVersionId;
        UserRoleVersion base = baseId == null ? null : requireVersion(track.getId(), baseId);
        UserRoleVersion version = createSnapshot(evaluation, improvement, track, base);
        UserRoleReactionRule rule = ruleService.applyImprovement(version.getId(), publicRule, improvement.getProposedRule());
        track.setLatestVersionId(version.getId());
        track.setLatestVersionNo(version.getVersionNo());
        trackMapper.updateById(track);
        return new VersionApplication(track, version, rule, baseId);
    }

    /**
     * 从公共基线或指定历史版本创建一个新快照，并在同一版本中应用有限条规则建议。
     *
     * @param evaluation 当前独立评测
     * @param batch 已审核的改进批次
     * @param changes 用户确认的规则改动
     * @param requestedBaseVersionId 可选历史基础版本
     * @return 新个人版本及对应规则快照
     */
    @Transactional
    public BatchVersionApplication applyBatch(RoleEvaluation evaluation,
                                              RoleEvaluationImprovementBatch batch,
                                              List<UserRoleReactionRuleService.RuleChange> changes,
                                              Long requestedBaseVersionId) {
        UserRoleTrack track = getOrCreateTrack(evaluation);
        Long baseId = requestedBaseVersionId == null ? batch.getUserRoleVersionId() : requestedBaseVersionId;
        UserRoleVersion base = baseId == null ? null : requireVersion(track.getId(), baseId);
        UserRoleVersion version = createSnapshot(evaluation, batch, track, base);
        List<UserRoleReactionRule> rules = ruleService.applyImprovements(version.getId(), changes);
        track.setLatestVersionId(version.getId());
        track.setLatestVersionNo(version.getVersionNo());
        trackMapper.updateById(track);
        return new BatchVersionApplication(track, version, rules, baseId);
    }

    private UserRoleTrack getOrCreateTrack(RoleEvaluation evaluation) {
        if (evaluation.getUserRoleTrackId() != null) {
            UserRoleTrack track = trackMapper.selectById(evaluation.getUserRoleTrackId());
            if (track == null || !Objects.equals(track.getUserId(), evaluation.getUserId())
                    || !Objects.equals(track.getCharacterId(), evaluation.getCharacterId())) {
                throw new IllegalStateException("评测绑定的个人角色演进轨迹无效");
            }
            return track;
        }
        UserRoleTrack track = findTrack(evaluation.getUserId(), evaluation.getCharacterId());
        if (track != null) return track;
        track = new UserRoleTrack();
        track.setUserId(evaluation.getUserId());
        track.setCharacterId(evaluation.getCharacterId());
        track.setLatestVersionNo(0);
        trackMapper.insert(track);
        return track;
    }

    private UserRoleVersion createSnapshot(RoleEvaluation evaluation, RoleEvaluationRuleImprovement improvement,
                                           UserRoleTrack track, UserRoleVersion base) {
        UserRoleVersion version = new UserRoleVersion();
        version.setUserRoleTrackId(track.getId());
        version.setVersionNo((track.getLatestVersionNo() == null ? 0 : track.getLatestVersionNo()) + 1);
        version.setParentVersionId(base == null ? null : base.getId());
        version.setSourceEvaluationId(evaluation.getId());
        version.setSourceImprovementId(improvement.getId());
        versionMapper.insert(version);
        profileService.copySnapshot(evaluation.getCharacterId(), base, version.getId());
        ruleService.copySnapshot(evaluation.getCharacterId(), base, version.getId());
        return version;
    }

    /**
     * 创建由一整个改进批次触发的个人版本快照。
     *
     * @param evaluation 当前独立评测
     * @param batch 规则改进批次
     * @param track 用户角色演进轨迹
     * @param base 基础版本；公共基线时为空
     * @return 已持久化的新版本
     */
    private UserRoleVersion createSnapshot(RoleEvaluation evaluation, RoleEvaluationImprovementBatch batch,
                                           UserRoleTrack track, UserRoleVersion base) {
        UserRoleVersion version = new UserRoleVersion();
        version.setUserRoleTrackId(track.getId());
        version.setVersionNo((track.getLatestVersionNo() == null ? 0 : track.getLatestVersionNo()) + 1);
        version.setParentVersionId(base == null ? null : base.getId());
        version.setSourceEvaluationId(evaluation.getId());
        version.setSourceImprovementBatchId(batch.getId());
        versionMapper.insert(version);
        profileService.copySnapshot(evaluation.getCharacterId(), base, version.getId());
        ruleService.copySnapshot(evaluation.getCharacterId(), base, version.getId());
        return version;
    }

    private UserRoleVersion requireVersion(Long trackId, Long versionId) {
        UserRoleVersion version = versionMapper.selectById(versionId);
        if (version == null || !Objects.equals(trackId, version.getUserRoleTrackId())) {
            throw new IllegalStateException("指定的历史个人版本不属于当前角色演进轨迹");
        }
        return version;
    }

    /** 版本应用后的聚合结果，供评测服务更新自身审计字段。 */
    public record VersionApplication(UserRoleTrack track, UserRoleVersion version,
                                     UserRoleReactionRule rule, Long baseVersionId) {
    }

    /** 批量规则应用后的新版本及规则快照集合。 */
    public record BatchVersionApplication(UserRoleTrack track, UserRoleVersion version,
                                          List<UserRoleReactionRule> rules, Long baseVersionId) {
    }
}
