package com.wuming.novel.integration.rpc.role;

import com.wuming.api.role.RoleRuntimeFacade;
import com.wuming.api.role.dto.RoleRuntimeContextDto;
import com.wuming.api.role.dto.RoleRuntimeContextResultDto;
import com.wuming.api.role.dto.RoleVersionValidationResultDto;
import com.wuming.novel.domain.entity.RoleCharacter;
import com.wuming.novel.domain.entity.RoleProfile;
import com.wuming.novel.domain.entity.UserRoleTrack;
import com.wuming.novel.domain.entity.UserRoleVersion;
import com.wuming.novel.infrastructure.mapper.UserRoleTrackMapper;
import com.wuming.novel.infrastructure.mapper.UserRoleVersionMapper;
import com.wuming.novel.service.IRoleCharacterService;
import com.wuming.novel.service.IRoleProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

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
    private final UserRoleTrackMapper userRoleTrackMapper;
    private final UserRoleVersionMapper userRoleVersionMapper;

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
     * 校验个人版本的轨迹、用户和公共角色完全一致，避免聊天会话绑定越权版本。
     */
    @Override
    public RoleVersionValidationResultDto validateUserRoleVersion(
            Long userId, Long characterId, Long userRoleVersionId) {
        if (userId == null || characterId == null || userRoleVersionId == null) {
            return RoleVersionValidationResultDto.failure("PARAM_ERROR", "角色版本校验参数不能为空");
        }
        UserRoleVersion version = userRoleVersionMapper.selectById(userRoleVersionId);
        if (version == null) {
            return RoleVersionValidationResultDto.failure("ROLE_VERSION_NOT_FOUND", "个人角色版本不存在");
        }
        UserRoleTrack track = userRoleTrackMapper.selectById(version.getUserRoleTrackId());
        if (track == null || !userId.equals(track.getUserId()) || !characterId.equals(track.getCharacterId())) {
            return RoleVersionValidationResultDto.failure("ROLE_VERSION_FORBIDDEN", "个人角色版本不属于当前用户或目标角色");
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
