package com.wuming.novel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.domain.entity.RoleCharacter;
import com.wuming.novel.infrastructure.mapper.RoleCharacterMapper;
import com.wuming.novel.service.IRoleCharacterService;
import org.springframework.stereotype.Service;

/**
 * 小说角色基础服务实现。
 */
@Service
public class RoleCharacterService
        extends ServiceImpl<RoleCharacterMapper, RoleCharacter>
        implements IRoleCharacterService {
}
