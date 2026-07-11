package com.wuming.novel.service.adjust;

import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import com.wuming.novel.domain.dto.CreateRoleAdjustRequest;
import com.wuming.novel.domain.entity.PersonalRoleTrack;
import com.wuming.novel.domain.entity.PersonalRoleVersion;
import com.wuming.novel.domain.entity.RoleAdjustRequest;
import com.wuming.novel.domain.entity.RoleCharacter;
import com.wuming.novel.domain.enums.RoleAdjustRequestStatus;
import com.wuming.novel.infrastructure.mapper.PersonalRoleTrackMapper;
import com.wuming.novel.infrastructure.mapper.PersonalRoleVersionMapper;
import com.wuming.novel.infrastructure.mapper.RoleAdjustRequestMapper;
import com.wuming.novel.service.IRoleCharacterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RoleAdjustService {

    private final IRoleCharacterService roleCharacterService;
    private final PersonalRoleVersionMapper roleVersionMapper;
    private final PersonalRoleTrackMapper roleTrackMapper;
    private final RoleAdjustRequestMapper requestMapper;

    /**
     * 创建等待生成候选调整项的用户请求。
     *
     * @param userId 当前认证用户主键
     * @param request 用户提交的调整要求
     * @return 已持久化的调整请求
     */
    public RoleAdjustRequest createRequest(Long userId, CreateRoleAdjustRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        if (request == null) {
            throw new IllegalArgumentException("调整请求不能为空");
        }
        requireCompletedCharacter(request.getCharacterId());
        requireOwnedBaseVersion(userId, request.getCharacterId(), request.getBaseVersionId());

        RoleAdjustRequest roleAdjustRequest = new RoleAdjustRequest();
        roleAdjustRequest.setUserId(userId);
        roleAdjustRequest.setCharacterId(request.getCharacterId());
        roleAdjustRequest.setBaseVersionId(request.getBaseVersionId());
        roleAdjustRequest.setRequirement(request.getRequirement());
        roleAdjustRequest.setChatText(request.getChatText());
        roleAdjustRequest.setStatus(RoleAdjustRequestStatus.PENDING);
        requestMapper.insert(roleAdjustRequest);
        return roleAdjustRequest;
    }

    /**
     * 校验目标公共角色存在且已完成构建，避免为不可聊天的角色创建调整请求。
     */
    private void requireCompletedCharacter(Long characterId) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId不能为空");
        }
        RoleCharacter character = roleCharacterService.getById(characterId);
        if (character == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公共角色不存在: " + characterId);
        }
        if (!"COMPLETED".equals(character.getBuildStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "公共角色尚未构建完成");
        }
    }

    /**
     * 校验用户选定的个人版本属于当前用户和目标公共角色。
     */
    private void requireOwnedBaseVersion(Long userId, Long characterId, Long baseVersionId) {
        if (baseVersionId == null) {
            return;
        }
        PersonalRoleVersion version = roleVersionMapper.selectById(baseVersionId);
        if (version == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "个人角色版本不存在: " + baseVersionId);
        }
        PersonalRoleTrack track = roleTrackMapper.selectById(version.getTrackId());
        if (track == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "个人角色版本轨迹不存在: " + version.getTrackId());
        }
        if (!Objects.equals(track.getUserId(), userId) || !Objects.equals(track.getCharacterId(), characterId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "个人角色版本不属于当前用户或目标角色");
        }
    }
}
