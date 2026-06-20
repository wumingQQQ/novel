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
    public boolean handleNovel(Long jobId) throws IOException {
        Job job = jobService.getById(jobId);
        if(job == null){
            throw new IllegalArgumentException("该job不存在，请创建后重试");
        }

        switch (job.getStage()) {
            case PENDING:
                if (!chapterService.splitChapter(jobId)) {
                    log.warn("job: {} 章节切分未完成，等待重试", jobId);
                    return false;
                }
                jobService.advanceStage(jobId, JobStage.CHAPTER_SPLIT);
            case CHAPTER_SPLIT:
                if (!layerService.splitLayer(jobId)) {
                    log.warn("job: {} 剧情分层未完成，等待重试", jobId);
                    return false;
                }
                jobService.advanceStage(jobId, JobStage.LAYER_SPLIT);
            case LAYER_SPLIT:
                if (!sceneService.splitScene(jobId)) {
                    log.warn("job: {} 场景切分未完成，等待重试", jobId);
                    return false;
                }
                jobService.advanceStage(jobId, JobStage.SCENE_SPLIT);
            case SCENE_SPLIT:
                if (!scenePoolService.divideSceneIntoPool(jobId)) {
                    log.warn("job: {} 场景分池未完成，等待重试", jobId);
                    return false;
                }
                jobService.advanceStage(jobId, JobStage.POOL_CLASSIFY);
            case POOL_CLASSIFY:
                if (!evidenceService.extractEvidence(jobId)) {
                    log.warn("job: {} 证据提取未完成，等待重试", jobId);
                    return false;
                }
                jobService.advanceStage(jobId, JobStage.EVIDENCE_EXTRACT);
            case EVIDENCE_EXTRACT:
                if (!aggregationService.aggregation(jobId)) {
                    log.warn("job: {} 画像聚合未完成，等待重试", jobId);
                    return false;
                }
                jobService.advanceStage(jobId, JobStage.PROFILE_AGGREGATION);
            case PROFILE_AGGREGATION:
                jobService.advanceStage(jobId, JobStage.COMPLETE);
        }
        return  true;
        // TODO 发邮件提醒用户任务完成
    }



}
