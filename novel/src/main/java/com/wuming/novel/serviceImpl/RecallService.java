package com.wuming.novel.serviceImpl;

import com.wuming.novel.domain.entity.Scene;
import com.wuming.novel.domain.entity.rel.ScenePool;
import com.wuming.novel.domain.enums.PoolType;
import com.wuming.novel.mapper.RecallMapper;
import com.wuming.novel.service.IScenePoolService;
import com.wuming.novel.service.ISceneService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.List;

@Service
public class RecallService {
    private final RecallMapper recallMapper;
    private final ISceneService sceneService;
    private final IScenePoolService scenePoolService;

    public RecallService(RecallMapper recallMapper, ISceneService sceneService, IScenePoolService scenePoolService) {
        this.recallMapper = recallMapper;
        this.sceneService = sceneService;
        this.scenePoolService = scenePoolService;
    }

    // TODO 后续考虑为不同池定义不同阈值
    @Min(0)
    @Max(1)
    @Value("${novel.recall.threshold}")
    private double threshold;

    @Max(50)
    @Value("${novel.recall.topK}")
    private int topK;

    // 按层召回
    public List<Scene> recallScenes(Long novelId, Long jobId, PoolType poolType, int startChapterSeq, int endChapterSeq) {
        return recallMapper.recallScenesByLayerAndPool(
                novelId,
                jobId,
                poolType.getCode(),
                threshold,
                startChapterSeq,
                endChapterSeq,
                topK
        );
    }

    public List<Scene> recallTopScenes(Long novelId, Long jobId, PoolType poolType, double threshold, int limit) {
        return recallMapper.recallTopScenesByPool(
                novelId,
                jobId,
                poolType.getCode(),
                threshold,
                limit
        );
    }
}
