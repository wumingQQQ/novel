package com.wuming.novel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wuming.novel.domain.entity.RoleProfile;

/**
 * 角色轻量画像摘要基础服务。
 */
public interface IRoleProfileService extends IService<RoleProfile> {

    /**
     * 为指定角色构建轻量画像。
     *
     * @param characterId 角色id
     * @return 是否成功保存画像
     */
    boolean buildProfile(Long characterId);
}
