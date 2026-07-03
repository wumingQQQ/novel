package com.wuming.novel.role.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.infrastructure.mapper.role.RoleCharacterMapper;
import com.wuming.novel.role.entity.RoleCharacter;
import com.wuming.novel.role.service.IRoleCharacterService;
import org.springframework.stereotype.Service;

/**
 * 小说角色基础服务实现。
 */
@Service
public class RoleCharacterService
        extends ServiceImpl<RoleCharacterMapper, RoleCharacter>
        implements IRoleCharacterService {
}
