package com.wuming.novel.serviceImpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.domain.entity.Layer;
import com.wuming.novel.mapper.LayerMapper;
import com.wuming.novel.service.ILayerService;
import org.springframework.stereotype.Service;

@Service
public class LayerService extends ServiceImpl<LayerMapper, Layer> implements ILayerService {
}
