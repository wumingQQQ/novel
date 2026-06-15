package com.wuming.novel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wuming.novel.domain.entity.Scene;

public interface ISceneService extends IService<Scene> {
    void splitScene(int id);
}
