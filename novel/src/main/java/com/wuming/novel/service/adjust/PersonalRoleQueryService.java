package com.wuming.novel.service.adjust;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.novel.domain.dto.PersonalRoleSummaryResponse;
import com.wuming.novel.domain.dto.PersonalRoleVersionResponse;
import com.wuming.novel.domain.entity.PersonalRoleTrack;
import com.wuming.novel.domain.entity.PersonalRoleVersion;
import com.wuming.novel.infrastructure.mapper.PersonalRoleTrackMapper;
import com.wuming.novel.infrastructure.mapper.PersonalRoleVersionMapper;
import com.wuming.novel.service.publicrole.RolePublicService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 当前用户个人角色版本查询服务。
 */
@Service
@RequiredArgsConstructor
public class PersonalRoleQueryService {
    private final PersonalRoleTrackMapper trackMapper;
    private final PersonalRoleVersionMapper versionMapper;
    private final RolePublicService rolePublicService;

    /**
     * 按个人角色轨迹聚合当前用户的最新个人版本，用于“我的角色”列表。
     *
     * <p>列表只返回创建聊天所需的版本标识和公共角色脱敏侧影；完整调整补丁仍由按角色查询版本接口提供。</p>
     *
     * @param userId 当前认证用户主键
     * @return 按轨迹最近更新时间倒序排列的个人角色摘要
     */
    public List<PersonalRoleSummaryResponse> listLatestRoles(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        List<PersonalRoleTrack> tracks = trackMapper.selectList(new LambdaQueryWrapper<PersonalRoleTrack>()
                .eq(PersonalRoleTrack::getUserId, userId)
                .isNotNull(PersonalRoleTrack::getLatestVersionId)
                .orderByDesc(PersonalRoleTrack::getUpdateTime)
                .orderByDesc(PersonalRoleTrack::getId));
        if (tracks == null || tracks.isEmpty()) {
            return List.of();
        }
        return tracks.stream()
                .map(this::toLatestRoleSummary)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 查询当前用户在指定公共角色下的全部个人版本，按版本号倒序返回。
     *
     * @param userId 当前认证用户主键
     * @param characterId 公共角色主键
     * @return 个人角色版本列表；没有轨迹时返回空列表
     */
    public List<PersonalRoleVersionResponse> listVersions(Long userId, Long characterId) {
        PersonalRoleTrack track = findTrack(userId, characterId);
        if (track == null) {
            return List.of();
        }
        List<PersonalRoleVersion> versions = versionMapper.selectList(new LambdaQueryWrapper<PersonalRoleVersion>()
                .eq(PersonalRoleVersion::getTrackId, track.getId())
                .orderByDesc(PersonalRoleVersion::getVersionNo));
        if (versions == null || versions.isEmpty()) {
            return List.of();
        }
        return versions.stream()
                .map(version -> toResponse(track, version))
                .toList();
    }

    /**
     * 查询当前用户在指定公共角色下的最新个人版本。
     *
     * @param userId 当前认证用户主键
     * @param characterId 公共角色主键
     * @return 最新个人角色版本；没有轨迹或没有版本时返回null
     */
    public PersonalRoleVersionResponse getLatestVersion(Long userId, Long characterId) {
        PersonalRoleTrack track = findTrack(userId, characterId);
        if (track == null) {
            return null;
        }
        PersonalRoleVersion version = loadLatestVersion(track);
        return version == null ? null : toResponse(track, version);
    }

    /**
     * 只按当前用户和公共角色查找轨迹，避免暴露其他用户个人版本。
     */
    private PersonalRoleTrack findTrack(Long userId, Long characterId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        if (characterId == null) {
            throw new IllegalArgumentException("characterId不能为空");
        }
        return trackMapper.selectOne(new LambdaQueryWrapper<PersonalRoleTrack>()
                .eq(PersonalRoleTrack::getUserId, userId)
                .eq(PersonalRoleTrack::getCharacterId, characterId));
    }

    /**
     * 优先使用轨迹上的latestVersionId；缺失时按版本号倒序兜底读取。
     */
    private PersonalRoleVersion loadLatestVersion(PersonalRoleTrack track) {
        if (track.getLatestVersionId() != null) {
            PersonalRoleVersion version = versionMapper.selectById(track.getLatestVersionId());
            if (version != null && Objects.equals(version.getTrackId(), track.getId())) {
                return version;
            }
        }
        return versionMapper.selectOne(new LambdaQueryWrapper<PersonalRoleVersion>()
                .eq(PersonalRoleVersion::getTrackId, track.getId())
                .orderByDesc(PersonalRoleVersion::getVersionNo)
                .last("limit 1"));
    }

    private PersonalRoleVersionResponse toResponse(PersonalRoleTrack track, PersonalRoleVersion version) {
        return new PersonalRoleVersionResponse(
                version.getId(),
                track.getCharacterId(),
                version.getVersionNo(),
                version.getParentVersionId(),
                version.getSourceRequestId(),
                Objects.equals(version.getId(), track.getLatestVersionId()),
                version.getCreateTime(),
                toBehaviorAdjustments(version.getBehaviorAdjustmentsSnapshot())
        );
    }

    /**
     * 读取轨迹最新版本并组合公共角色脱敏侧影；异常或脏轨迹不会返回半成品列表项。
     */
    private PersonalRoleSummaryResponse toLatestRoleSummary(PersonalRoleTrack track) {
        PersonalRoleVersion version = loadLatestVersion(track);
        if (version == null) {
            return null;
        }
        return new PersonalRoleSummaryResponse(
                rolePublicService.getPreview(track.getCharacterId()),
                version.getId(),
                version.getVersionNo(),
                version.getSourceRequestId(),
                version.getCreateTime());
    }

    private List<PersonalRoleVersionResponse.BehaviorAdjustment> toBehaviorAdjustments(
            List<PersonalRoleVersion.BehaviorAdjustmentSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }
        return snapshots.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        PersonalRoleVersion.BehaviorAdjustmentSnapshot::getDisplayOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toBehaviorAdjustment)
                .toList();
    }

    private PersonalRoleVersionResponse.BehaviorAdjustment toBehaviorAdjustment(
            PersonalRoleVersion.BehaviorAdjustmentSnapshot snapshot) {
        return new PersonalRoleVersionResponse.BehaviorAdjustment(
                snapshot.getAdjustmentId(),
                snapshot.getSourceAdjustItemId(),
                snapshot.getApplicability(),
                snapshot.getExpectedBehavior(),
                snapshot.getForbiddenBehavior(),
                snapshot.getDisplayOrder()
        );
    }
}
