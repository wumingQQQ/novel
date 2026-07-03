package com.wuming.novel.role.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.infrastructure.mapper.role.NovelPassageMapper;
import com.wuming.novel.role.entity.NovelPassage;
import com.wuming.novel.role.service.INovelPassageService;
import org.springframework.stereotype.Service;

/**
 * 小说检索文本块基础服务实现。
 */
@Service
public class NovelPassageService
        extends ServiceImpl<NovelPassageMapper, NovelPassage>
        implements INovelPassageService {
}
