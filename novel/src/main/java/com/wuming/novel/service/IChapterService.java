package com.wuming.novel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wuming.novel.domain.entity.Chapter;

public interface IChapterService extends IService<Chapter> {
    void splitChapter(Long jobId);
}
