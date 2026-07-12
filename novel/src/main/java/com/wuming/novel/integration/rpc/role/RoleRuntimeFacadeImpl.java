package com.wuming.novel.integration.rpc.role;

import com.wuming.api.role.RoleRuntimeFacade;
import com.wuming.api.role.dto.RoleRuntimeContextDto;
import com.wuming.api.role.dto.RoleRuntimeContextResultDto;
import com.wuming.api.role.dto.RoleVersionValidationResultDto;
import com.wuming.novel.domain.entity.PersonalRoleTrack;
import com.wuming.novel.domain.entity.PersonalRoleVersion;
import com.wuming.novel.domain.entity.RoleCharacter;
import com.wuming.novel.domain.entity.RoleProfile;
import com.wuming.novel.infrastructure.mapper.PersonalRoleTrackMapper;
import com.wuming.novel.infrastructure.mapper.PersonalRoleVersionMapper;
import com.wuming.novel.service.IRoleCharacterService;
import com.wuming.novel.service.IRoleProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 角色运行时远程接口实现。
 */
@Slf4j
@DubboService
@RequiredArgsConstructor
public class RoleRuntimeFacadeImpl implements RoleRuntimeFacade {
    private static final String COMPLETED = "COMPLETED";

    private final IRoleCharacterService roleCharacterService;
    private final IRoleProfileService roleProfileService;
    private final PersonalRoleTrackMapper personalRoleTrackMapper;
    private final PersonalRoleVersionMapper personalRoleVersionMapper;

    @Override
    public RoleRuntimeContextResultDto getRuntimeContext(Long characterId) {
        if (characterId == null) {
            return RoleRuntimeContextResultDto.failure("PARAM_ERROR", "characterId不能为空");
        }

        RoleCharacter character = roleCharacterService.getById(characterId);
        if (character == null) {
            return RoleRuntimeContextResultDto.failure("ROLE_CHARACTER_NOT_FOUND",
                    "角色不存在: " + characterId);
        }
        if (!COMPLETED.equals(character.getBuildStatus())) {
            String reason = character.getBuildError() == null || character.getBuildError().isBlank()
                    ? "角色构建未完成"
                    : character.getBuildError();
            return RoleRuntimeContextResultDto.failure("ROLE_RUNTIME_INCOMPLETE", reason);
        }

        RoleProfile profile = roleProfileService.lambdaQuery()
                .eq(RoleProfile::getCharacterId, character.getId())
                .one();
        if (profile == null) {
            return RoleRuntimeContextResultDto.failure("ROLE_PROFILE_NOT_FOUND",
                    "角色画像不存在: " + character.getCharacterName());
        }

        return RoleRuntimeContextResultDto.success(toContext(character, profile));
    }

    /**
     * 获取公共角色运行时上下文，并按需叠加当前用户的个人行为调整补丁。
     *
     * @param userId 当前用户主键
     * @param characterId 公共角色主键
     * @param userRoleVersionId 个人角色版本主键，可为空
     * @return 可直接用于聊天提示词构建的运行时上下文
     */
    @Override
    public RoleRuntimeContextResultDto getRuntimeContext(
            Long userId, Long characterId, Long userRoleVersionId) {
        if (userRoleVersionId == null) {
            return getRuntimeContext(characterId);
        }

        RoleRuntimeContextResultDto baseResult = getRuntimeContext(characterId);
        if (baseResult == null || !baseResult.isSuccess()) {
            return baseResult;
        }

        PersonalRoleVersion version = personalRoleVersionMapper.selectById(userRoleVersionId);
        RoleVersionValidationResultDto validation = validateVersionOwnership(
                userId, characterId, userRoleVersionId, version);
        if (!validation.isValid()) {
            return RoleRuntimeContextResultDto.failure(validation.getCode(), validation.getMessage());
        }

        RoleRuntimeContextDto context = baseResult.getRuntimeContext();
        context.setBehaviorAdjustments(toBehaviorAdjustments(version.getBehaviorAdjustmentsSnapshot()));
        return RoleRuntimeContextResultDto.success(context);
    }

    /**
     * 校验个人角色版本归属，防止聊天会话绑定其他用户或其他公共角色的版本。
     */
    @Override
    public RoleVersionValidationResultDto validateUserRoleVersion(
            Long userId, Long characterId, Long userRoleVersionId) {
        PersonalRoleVersion version = userRoleVersionId == null
                ? null
                : personalRoleVersionMapper.selectById(userRoleVersionId);
        return validateVersionOwnership(userId, characterId, userRoleVersionId, version);
    }

    private RoleVersionValidationResultDto validateVersionOwnership(
            Long userId,
            Long characterId,
            Long userRoleVersionId,
            PersonalRoleVersion version) {
        if (userId == null || characterId == null || userRoleVersionId == null) {
            return RoleVersionValidationResultDto.failure("PARAM_ERROR", "用户、角色和个人版本不能为空");
        }
        if (version == null) {
            return RoleVersionValidationResultDto.failure("ROLE_VERSION_NOT_FOUND",
                    "个人角色版本不存在: " + userRoleVersionId);
        }
        PersonalRoleTrack track = personalRoleTrackMapper.selectById(version.getTrackId());
        if (track == null) {
            return RoleVersionValidationResultDto.failure("ROLE_VERSION_TRACK_NOT_FOUND",
                    "个人角色版本轨迹不存在: " + version.getTrackId());
        }
        if (!Objects.equals(track.getUserId(), userId)
                || !Objects.equals(track.getCharacterId(), characterId)) {
            return RoleVersionValidationResultDto.failure("ROLE_VERSION_FORBIDDEN",
                    "个人角色版本不属于当前用户或目标角色");
        }
        return RoleVersionValidationResultDto.success();
    }

    private RoleRuntimeContextDto toContext(RoleCharacter character, RoleProfile profile) {
        RoleRuntimeContextDto context = new RoleRuntimeContextDto();
        context.setNovelId(character.getNovelId());
        context.setNovelName(character.getNovelName());
        context.setCharacterId(character.getId());
        context.setCharacterName(character.getCharacterName());
        context.setBuildStatus(character.getBuildStatus());
        context.setBasicInfo(toBasicInfo(profile.getBasicInfo()));
        context.setCoreTraits(profile.getCoreTraits());
        context.setSpeakingStyle(toSpeakingStyle(profile.getSpeakingStyle()));
        context.setForbiddenBehaviors(profile.getForbiddenBehaviors());
        return context;
    }

    private List<RoleRuntimeContextDto.BehaviorAdjustment> toBehaviorAdjustments(
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

    private RoleRuntimeContextDto.BehaviorAdjustment toBehaviorAdjustment(
            PersonalRoleVersion.BehaviorAdjustmentSnapshot source) {
        RoleRuntimeContextDto.BehaviorAdjustment target = new RoleRuntimeContextDto.BehaviorAdjustment();
        target.setAdjustmentId(source.getAdjustmentId());
        target.setApplicability(source.getApplicability());
        target.setExpectedBehavior(source.getExpectedBehavior());
        target.setForbiddenBehavior(source.getForbiddenBehavior());
        target.setDisplayOrder(source.getDisplayOrder());
        return target;
    }

    private RoleRuntimeContextDto.BasicInfo toBasicInfo(RoleProfile.BasicInfo source) {
        RoleRuntimeContextDto.BasicInfo target = new RoleRuntimeContextDto.BasicInfo();
        if (source == null) {
            return target;
        }
        target.setAge(source.getAge());
        target.setGender(source.getGender());
        target.setOccupation(source.getOccupation());
        target.setAppearance(source.getAppearance());
        return target;
    }

    private RoleRuntimeContextDto.SpeakingStyle toSpeakingStyle(RoleProfile.SpeakingStyle source) {
        RoleRuntimeContextDto.SpeakingStyle target = new RoleRuntimeContextDto.SpeakingStyle();
        if (source == null) {
            return target;
        }
        target.setSignature(source.getSignature());
        target.setDistinctivePatterns(source.getDistinctivePatterns());
        target.setAvoidPatterns(source.getAvoidPatterns());
        return target;
    }
}
