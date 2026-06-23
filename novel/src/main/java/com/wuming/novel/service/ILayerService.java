package com.wuming.novel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wuming.novel.domain.entity.Layer;

public interface ILayerService extends IService<Layer> {
    void splitLayer(Long jobId);
}
