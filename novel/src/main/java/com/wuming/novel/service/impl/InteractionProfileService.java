package com.wuming.novel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.domain.entity.InteractionProfile;
import com.wuming.novel.infrastructure.mapper.InteractionProfileMapper;
import com.wuming.novel.service.IInteractionProfileService;
import org.springframework.stereotype.Service;

@Service
public class InteractionProfileService extends ServiceImpl<InteractionProfileMapper, InteractionProfile> implements IInteractionProfileService {
}
