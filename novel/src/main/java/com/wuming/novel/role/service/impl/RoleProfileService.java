package com.wuming.novel.role.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.infrastructure.mapper.role.RoleProfileMapper;
import com.wuming.novel.role.entity.RoleProfile;
import com.wuming.novel.role.service.IRoleProfileService;
import org.springframework.stereotype.Service;

/**
 * 角色轻量画像摘要基础服务实现。
 */
@Service
public class RoleProfileService
        extends ServiceImpl<RoleProfileMapper, RoleProfile>
        implements IRoleProfileService {
}
