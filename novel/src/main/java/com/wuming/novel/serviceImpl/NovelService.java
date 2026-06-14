package com.wuming.novel.serviceImpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.domain.entity.Novel;
import com.wuming.novel.mapper.NovelMapper;
import com.wuming.novel.service.INovelService;
import org.springframework.stereotype.Service;

@Service
public class NovelService extends ServiceImpl<NovelMapper, Novel> implements INovelService {
}
