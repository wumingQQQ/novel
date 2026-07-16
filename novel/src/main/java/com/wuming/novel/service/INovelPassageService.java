package com.wuming.novel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wuming.novel.domain.entity.NovelPassage;

import java.util.List;

/**
 * 小说检索文本块基础服务。
 */
public interface INovelPassageService extends IService<NovelPassage> {

    /**
     * 按章节内容切分Passage，替换该章节旧Passage，并在事务外同步刷新向量索引。
     *
     * @param jobId 任务id，用于日志追踪
     * @param chapterId 章节id
     * @return 本次切分后保存的新Passage列表
     */
    List<NovelPassage> splitPassage(Long jobId, Long chapterId);
}
