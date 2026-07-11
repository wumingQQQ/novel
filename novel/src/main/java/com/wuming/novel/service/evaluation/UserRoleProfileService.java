package com.wuming.novel.service.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.novel.domain.entity.RoleProfile;
import com.wuming.novel.domain.entity.UserRoleProfile;
import com.wuming.novel.domain.entity.UserRoleVersion;
import com.wuming.novel.infrastructure.mapper.RoleProfileMapper;
import com.wuming.novel.infrastructure.mapper.UserRoleProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 管理用户角色版本的完整画像快照。
 */
@Service
@RequiredArgsConstructor
public class UserRoleProfileService {
    private final RoleProfileMapper roleProfileMapper;
    private final UserRoleProfileMapper userRoleProfileMapper;

    /** 读取公共画像或指定个人版本的画像快照。 */
    public RoleProfile loadEffectiveProfile(Long characterId, Long versionId) {
        if (versionId != null) {
            UserRoleProfile snapshot = userRoleProfileMapper.selectOne(new LambdaQueryWrapper<UserRoleProfile>()
                    .eq(UserRoleProfile::getUserRoleVersionId, versionId));
            if (snapshot == null) throw new IllegalStateException("个人角色版本缺少画像快照");
            RoleProfile result = new RoleProfile();
            result.setBasicInfo(snapshot.getBasicInfo());
            result.setCoreTraits(snapshot.getCoreTraits());
            result.setSpeakingStyle(snapshot.getSpeakingStyle());
            result.setForbiddenBehaviors(snapshot.getForbiddenBehaviors());
            return result;
        }
        RoleProfile publicProfile = roleProfileMapper.selectOne(new LambdaQueryWrapper<RoleProfile>()
                .eq(RoleProfile::getCharacterId, characterId));
        if (publicProfile == null) throw new IllegalStateException("角色画像不存在");
        return publicProfile;
    }

    /** 将公共画像或历史版本画像复制为新版本的完整快照。 */
    public void copySnapshot(Long characterId, UserRoleVersion baseVersion, Long targetVersionId) {
        RoleProfile source = loadEffectiveProfile(characterId, baseVersion == null ? null : baseVersion.getId());
        UserRoleProfile snapshot = new UserRoleProfile();
        snapshot.setUserRoleVersionId(targetVersionId);
        snapshot.setBasicInfo(source.getBasicInfo());
        snapshot.setCoreTraits(source.getCoreTraits());
        snapshot.setSpeakingStyle(source.getSpeakingStyle());
        snapshot.setForbiddenBehaviors(source.getForbiddenBehaviors());
        userRoleProfileMapper.insert(snapshot);
    }
}
