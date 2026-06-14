package com.wuming.novel.serviceImpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.domain.entity.Scene;
import com.wuming.novel.mapper.SceneMapper;
import com.wuming.novel.service.ISceneService;
import org.springframework.stereotype.Service;

@Service
public class SceneService extends ServiceImpl<SceneMapper, Scene> implements ISceneService {
}
