package com.wuming.novel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wuming.novel.domain.entity.NovelPassage;
import com.wuming.novel.domain.entity.PassageCharacter;

import java.util.List;

/**
 * Passage 出场角色映射基础服务。
 */
public interface IPassageCharacterService extends IService<PassageCharacter> {
    void recognizeAndSave(List<NovelPassage> passages);
}
