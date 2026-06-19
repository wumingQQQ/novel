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

        int novelId = job.getNovelId();

        advanceStage(jobId, JobStage.CHAPTER_SPLIT);
        log.info("job: {}进入{}阶段", jobId, JobStage.CHAPTER_SPLIT.name());
        chapterService.splitChapter(novelId);
        log.info("job: {}完成阶段{}", jobId, JobStage.CHAPTER_SPLIT.name());

        advanceStage(jobId, JobStage.LAYER_SPLIT);
        log.info("job: {}进入{}阶段", jobId, JobStage.LAYER_SPLIT.name());
        layerService.splitLayer(novelId);
        log.info("job: {}完成阶段{}", jobId, JobStage.LAYER_SPLIT.name());

        advanceStage(jobId, JobStage.SCENE_SPLIT);
        log.info("job: {}进入{}阶段", jobId, JobStage.SCENE_SPLIT.name());
        sceneService.splitScene(novelId);
        log.info("job: {}完成阶段{}", jobId, JobStage.SCENE_SPLIT.name());

        advanceStage(jobId, JobStage.POOL_CLASSIFY);
        log.info("job: {}进入{}阶段", jobId, JobStage.POOL_CLASSIFY.name());
        scenePoolService.divideSceneIntoPool(novelId);
        log.info("job: {}完成阶段{}", jobId, JobStage.POOL_CLASSIFY.name());

        advanceStage(jobId, JobStage.EVIDENCE_EXTRACT);
        log.info("job: {}进入{}阶段", jobId, JobStage.EVIDENCE_EXTRACT.name());
        evidenceService.extractEvidence(novelId);
        log.info("job: {}完成阶段{}", jobId, JobStage.EVIDENCE_EXTRACT.name());

        advanceStage(jobId, JobStage.PROFILE_AGGREGATION);
        log.info("job: {}进入{}阶段", jobId, JobStage.PROFILE_AGGREGATION.name());
        aggregationService.aggregation(novelId);
        log.info("job: {}完成阶段{}", jobId, JobStage.PROFILE_AGGREGATION.name());

        advanceStage(jobId, JobStage.COMPLETE);
        log.info("job: {}完成", jobId);
        // TODO 发邮件提醒用户任务完成
    }

    public void advanceStage(int jobId, JobStage stage) throws IOException {
        Job job = jobService.getById(jobId);
        job.setStage(stage);
        jobService.updateById(job);
    }

}
