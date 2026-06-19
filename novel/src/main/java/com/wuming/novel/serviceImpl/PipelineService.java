package com.wuming.novel.serviceImpl;

import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@Slf4j
@RequiredArgsConstructor
public class PipelineService {
    private final INovelService novelService;
    private final IJobService jobService;
    private final IChapterService chapterService;
    private final ILayerService layerService;
    private final ISceneService sceneService;
    private final IScenePoolService scenePoolService;
    private final IEvidenceService evidenceService;
    private final AggregationService aggregationService;

    // TODO 将来各个阶段抛出异常则从原处恢复
    // TODO 针对前面几个与job无关的阶段可以检测是否已经完成，完成则跳过
    public void handleNovel(int jobId) throws IOException {
        Job job = jobService.getById(jobId);
        if(job == null){
            throw new IllegalArgumentException("该job不存在，请创建后重试");
        }

        switch (job.getStage()) {
            case PENDING:
                chapterService.splitChapter(jobId);
                jobService.advanceStage(jobId, JobStage.CHAPTER_SPLIT);
                // fall through 继续下一步
            case CHAPTER_SPLIT:
                layerService.splitLayer(jobId);
                jobService.advanceStage(jobId, JobStage.LAYER_SPLIT);
            case LAYER_SPLIT:
                sceneService.splitScene(jobId);
                jobService.advanceStage(jobId, JobStage.SCENE_SPLIT);
            case SCENE_SPLIT:
                scenePoolService.divideSceneIntoPool(jobId);
                jobService.advanceStage(jobId, JobStage.POOL_CLASSIFY);
            case POOL_CLASSIFY:
                evidenceService.extractEvidence(jobId);
                jobService.advanceStage(jobId, JobStage.EVIDENCE_EXTRACT);
            case EVIDENCE_EXTRACT:
                aggregationService.aggregation(jobId);
                jobService.advanceStage(jobId, JobStage.PROFILE_AGGREGATION);
            case PROFILE_AGGREGATION:
                jobService.advanceStage(jobId, JobStage.COMPLETE);
        }
        // TODO 发邮件提醒用户任务完成
    }



}
