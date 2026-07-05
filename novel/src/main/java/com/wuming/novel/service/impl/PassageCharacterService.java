package com.wuming.novel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.domain.entity.PassageCharacter;
import com.wuming.novel.infrastructure.mapper.PassageCharacterMapper;
import com.wuming.novel.service.IPassageCharacterService;
import org.springframework.stereotype.Service;

/**
 * Passage 出场角色映射基础服务实现。
 */
@Service
public class PassageCharacterService
        extends ServiceImpl<PassageCharacterMapper, PassageCharacter>
        implements IPassageCharacterService {
}
