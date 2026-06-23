package com.wuming.novel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wuming.novel.domain.entity.rel.ScenePool;

public interface IScenePoolService extends IService<ScenePool> {
    void divideSceneIntoPool(Long jobId);
}
