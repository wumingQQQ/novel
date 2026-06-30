package com.wuming.novel.serviceImpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.domain.entity.CharacterProfile;
import com.wuming.novel.infrastructure.mapper.CharacterProfileMapper;
import com.wuming.novel.service.ICharacterProfileService;
import org.springframework.stereotype.Service;

@Service
public class CharacterProfileService extends ServiceImpl<CharacterProfileMapper, CharacterProfile> implements ICharacterProfileService {
}
