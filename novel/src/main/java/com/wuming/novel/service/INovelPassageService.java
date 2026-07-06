package com.wuming.novel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wuming.novel.domain.entity.NovelPassage;

/**
 * 小说检索文本块基础服务。
 */
public interface INovelPassageService extends IService<NovelPassage> {
    void splitPassage(Long jobId, Long chapterId);
}
