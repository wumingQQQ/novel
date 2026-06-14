package com.wuming.novel.serviceImpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.mapper.ChapterMapper;
import com.wuming.novel.service.IChapterService;
import org.springframework.stereotype.Service;

@Service
public class ChapterService extends ServiceImpl<ChapterMapper, Chapter> implements IChapterService {
}
