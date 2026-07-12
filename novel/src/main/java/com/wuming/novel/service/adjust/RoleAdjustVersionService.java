package com.wuming.novel.service.adjust;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.novel.domain.entity.PersonalRoleTrack;
import com.wuming.novel.domain.entity.PersonalRoleVersion;
import com.wuming.novel.domain.entity.RoleAdjustItem;
import com.wuming.novel.domain.entity.RoleAdjustRequest;
import com.wuming.novel.domain.enums.RoleAdjustChangeType;
import com.wuming.novel.domain.enums.RoleAdjustRequestStatus;
import com.wuming.novel.domain.enums.RoleAdjustStatus;
import com.wuming.novel.infrastructure.mapper.PersonalRoleTrackMapper;
import com.wuming.novel.infrastructure.mapper.PersonalRoleVersionMapper;
import com.wuming.novel.infrastructure.mapper.RoleAdjustItemMapper;
import com.wuming.novel.infrastructure.mapper.RoleAdjustRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 将已确认的角色调整请求落地为用户个人角色版本。
 */
@Service
@RequiredArgsConstructor
public class RoleAdjustVersionService {
    private final RoleAdjustRequestMapper requestMapper;
    private final RoleAdjustItemMapper itemMapper;
    private final PersonalRoleTrackMapper trackMapper;
    private final PersonalRoleVersionMapper versionMapper;

    /**
     * 基于当前用户已确认的调整请求创建不可变个人角色版本。
     *
     * @param userId 当前认证用户主键
     * @param requestId 调整请求主键
     * @return 新创建的个人角色版本主键
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createVersion(Long userId, Long requestId) {
        RoleAdjustRequest request = requireConfirmedRequest(userId, requestId);
        if (request.getCreatedVersionId() != null) {
            return request.getCreatedVersionId();
        }

        List<RoleAdjustItem> acceptedItems = loadAcceptedItems(requestId);
        if (acceptedItems.isEmpty()) {
            throw new IllegalStateException("没有已接受的调整项，无法创建个人角色版本");
        }

        PersonalRoleTrack track = getOrCreateTrack(request);
        PersonalRoleVersion baseVersion = loadBaseVersion(request.getBaseVersionId(), track.getId());
        List<PersonalRoleVersion.BehaviorAdjustmentSnapshot> snapshots =
                applyAcceptedItems(baseSnapshots(baseVersion), acceptedItems);

        PersonalRoleVersion version = new PersonalRoleVersion();
        version.setTrackId(track.getId());
        version.setVersionNo(nextVersionNo(track));
        version.setParentVersionId(request.getBaseVersionId());
        version.setSourceRequestId(request.getId());
        version.setBehaviorAdjustmentsSnapshot(snapshots);
        versionMapper.insert(version);

        updateTrack(track.getId(), version.getId(), version.getVersionNo());
        markCompleted(request.getId(), version.getId());
        return version.getId();
    }

    /**
     * 校验请求存在、属于当前用户且已经完成评审。
     */
    private RoleAdjustRequest requireConfirmedRequest(Long userId, Long requestId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        if (requestId == null) {
            throw new IllegalArgumentException("requestId不能为空");
        }
        RoleAdjustRequest request = requestMapper.selectById(requestId);
        if (request == null) {
            throw new IllegalArgumentException("角色调整请求不存在: " + requestId);
        }
        if (!Objects.equals(request.getUserId(), userId)) {
            throw new IllegalStateException("角色调整请求不属于当前用户");
        }
        if (request.getStatus() != RoleAdjustRequestStatus.CONFIRMED
                && request.getStatus() != RoleAdjustRequestStatus.COMPLETED) {
            throw new IllegalStateException("只有CONFIRMED状态的角色调整请求可以创建个人版本");
        }
        return request;
    }

    /**
     * 读取用户已接受的调整项，按展示顺序应用。
     */
    private List<RoleAdjustItem> loadAcceptedItems(Long requestId) {
        List<RoleAdjustItem> items = itemMapper.selectList(new LambdaQueryWrapper<RoleAdjustItem>()
                .eq(RoleAdjustItem::getRequestId, requestId)
                .eq(RoleAdjustItem::getStatus, RoleAdjustStatus.ACCEPTED)
                .orderByAsc(RoleAdjustItem::getDisplayOrder));
        return items == null ? List.of() : items;
    }

    /**
     * 获取用户在目标角色下唯一的版本轨迹，不存在则创建。
     */
    private PersonalRoleTrack getOrCreateTrack(RoleAdjustRequest request) {
        PersonalRoleTrack track = trackMapper.selectOne(new LambdaQueryWrapper<PersonalRoleTrack>()
                .eq(PersonalRoleTrack::getUserId, request.getUserId())
                .eq(PersonalRoleTrack::getCharacterId, request.getCharacterId()));
        if (track != null) {
            return track;
        }
        track = new PersonalRoleTrack();
        track.setUserId(request.getUserId());
        track.setCharacterId(request.getCharacterId());
        track.setLatestVersionNo(0);
        trackMapper.insert(track);
        return track;
    }

    /**
     * 基线版本只能来自当前用户在同一公共角色下的演进轨迹。
     */
    private PersonalRoleVersion loadBaseVersion(Long baseVersionId, Long trackId) {
        if (baseVersionId == null) {
            return null;
        }
        PersonalRoleVersion baseVersion = versionMapper.selectById(baseVersionId);
        if (baseVersion == null) {
            throw new IllegalStateException("基线个人角色版本不存在: " + baseVersionId);
        }
        if (!Objects.equals(baseVersion.getTrackId(), trackId)) {
            throw new IllegalStateException("基线个人角色版本不属于当前用户角色轨迹");
        }
        return baseVersion;
    }

    private int nextVersionNo(PersonalRoleTrack track) {
        return (track.getLatestVersionNo() == null ? 0 : track.getLatestVersionNo()) + 1;
    }

    private List<PersonalRoleVersion.BehaviorAdjustmentSnapshot> baseSnapshots(PersonalRoleVersion baseVersion) {
        if (baseVersion == null || baseVersion.getBehaviorAdjustmentsSnapshot() == null) {
            return new ArrayList<>();
        }
        return baseVersion.getBehaviorAdjustmentsSnapshot().stream()
                .filter(Objects::nonNull)
                .map(this::copySnapshot)
                .sorted(Comparator.comparing(PersonalRoleVersion.BehaviorAdjustmentSnapshot::getDisplayOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    /**
     * 将本次已接受调整应用到基线快照上，生成新版本快照。
     */
    private List<PersonalRoleVersion.BehaviorAdjustmentSnapshot> applyAcceptedItems(
            List<PersonalRoleVersion.BehaviorAdjustmentSnapshot> snapshots,
            List<RoleAdjustItem> acceptedItems) {
        Map<String, PersonalRoleVersion.BehaviorAdjustmentSnapshot> byAdjustmentId = new LinkedHashMap<>();
        for (PersonalRoleVersion.BehaviorAdjustmentSnapshot snapshot : snapshots) {
            byAdjustmentId.put(snapshot.getAdjustmentId(), snapshot);
        }

        for (RoleAdjustItem item : acceptedItems) {
            if (item.getChangeType() == RoleAdjustChangeType.ADD) {
                PersonalRoleVersion.BehaviorAdjustmentSnapshot snapshot = toNewSnapshot(item, snapshots.size() + 1);
                snapshots.add(snapshot);
                byAdjustmentId.put(snapshot.getAdjustmentId(), snapshot);
            } else if (item.getChangeType() == RoleAdjustChangeType.REPLACE) {
                PersonalRoleVersion.BehaviorAdjustmentSnapshot target = requireTarget(byAdjustmentId, item);
                applyReplacement(target, item);
            } else if (item.getChangeType() == RoleAdjustChangeType.DISABLE) {
                PersonalRoleVersion.BehaviorAdjustmentSnapshot target = requireTarget(byAdjustmentId, item);
                snapshots.remove(target);
                byAdjustmentId.remove(target.getAdjustmentId());
            }
        }

        for (int i = 0; i < snapshots.size(); i++) {
            snapshots.get(i).setDisplayOrder(i + 1);
        }
        return snapshots;
    }

    private PersonalRoleVersion.BehaviorAdjustmentSnapshot requireTarget(
            Map<String, PersonalRoleVersion.BehaviorAdjustmentSnapshot> byAdjustmentId,
            RoleAdjustItem item) {
        String targetAdjustmentId = item.getTargetAdjustmentId();
        PersonalRoleVersion.BehaviorAdjustmentSnapshot target = byAdjustmentId.get(targetAdjustmentId);
        if (target == null) {
            throw new IllegalStateException("目标行为补丁不存在: " + targetAdjustmentId);
        }
        return target;
    }

    private PersonalRoleVersion.BehaviorAdjustmentSnapshot toNewSnapshot(RoleAdjustItem item, int displayOrder) {
        PersonalRoleVersion.BehaviorAdjustmentSnapshot snapshot = new PersonalRoleVersion.BehaviorAdjustmentSnapshot();
        snapshot.setAdjustmentId(item.getAdjustmentId() == null || item.getAdjustmentId().isBlank()
                ? UUID.randomUUID().toString()
                : item.getAdjustmentId());
        snapshot.setSourceAdjustItemId(item.getId());
        snapshot.setApplicability(item.getApplicability());
        snapshot.setExpectedBehavior(item.getExpectedBehavior());
        snapshot.setForbiddenBehavior(item.getForbiddenBehavior());
        snapshot.setDisplayOrder(displayOrder);
        return snapshot;
    }

    private void applyReplacement(PersonalRoleVersion.BehaviorAdjustmentSnapshot target, RoleAdjustItem item) {
        target.setSourceAdjustItemId(item.getId());
        target.setApplicability(item.getApplicability());
        target.setExpectedBehavior(item.getExpectedBehavior());
        target.setForbiddenBehavior(item.getForbiddenBehavior());
    }

    private PersonalRoleVersion.BehaviorAdjustmentSnapshot copySnapshot(
            PersonalRoleVersion.BehaviorAdjustmentSnapshot source) {
        PersonalRoleVersion.BehaviorAdjustmentSnapshot target = new PersonalRoleVersion.BehaviorAdjustmentSnapshot();
        target.setAdjustmentId(source.getAdjustmentId());
        target.setSourceAdjustItemId(source.getSourceAdjustItemId());
        target.setApplicability(source.getApplicability());
        target.setExpectedBehavior(source.getExpectedBehavior());
        target.setForbiddenBehavior(source.getForbiddenBehavior());
        target.setDisplayOrder(source.getDisplayOrder());
        return target;
    }

    private void updateTrack(Long trackId, Long versionId, Integer versionNo) {
        PersonalRoleTrack update = new PersonalRoleTrack();
        update.setId(trackId);
        update.setLatestVersionId(versionId);
        update.setLatestVersionNo(versionNo);
        trackMapper.updateById(update);
    }

    private void markCompleted(Long requestId, Long versionId) {
        RoleAdjustRequest update = new RoleAdjustRequest();
        update.setId(requestId);
        update.setStatus(RoleAdjustRequestStatus.COMPLETED);
        update.setCreatedVersionId(versionId);
        requestMapper.updateById(update);
    }
}
