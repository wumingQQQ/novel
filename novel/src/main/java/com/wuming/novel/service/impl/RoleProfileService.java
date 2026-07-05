package com.wuming.novel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.domain.entity.RoleProfile;
import com.wuming.novel.infrastructure.mapper.RoleProfileMapper;
import com.wuming.novel.service.IRoleProfileService;
import org.springframework.stereotype.Service;

/**
 * 角色轻量画像摘要基础服务实现。
 */
@Service
public class RoleProfileService
        extends ServiceImpl<RoleProfileMapper, RoleProfile>
        implements IRoleProfileService {
}
