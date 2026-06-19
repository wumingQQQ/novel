package com.wuming.novel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wuming.novel.domain.entity.Chapter;

import java.io.IOException;

public interface IChapterService extends IService<Chapter> {
    void splitChapter(int jobId) throws IOException;
}
