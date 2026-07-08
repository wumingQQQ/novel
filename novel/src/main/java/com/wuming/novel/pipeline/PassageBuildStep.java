package com.wuming.novel.pipeline;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.NovelPassage;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.INovelPassageService;
import com.wuming.novel.service.IPassageCharacterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Passage构建流程阶段。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PassageBuildStep implements PipelineStep {
    private final IJobService jobService;
    private final IChapterService chapterService;
    private final INovelPassageService novelPassageService;
    private final IPassageCharacterService passageCharacterService;

    @Override
    public JobStage stage() {
        return JobStage.PASSAGE_BUILD;
    }

    @Override
    public String name() {
        return "Passage构建";
    }

    @Override
    public void execute(Long jobId) {
        Job job = requireJob(jobId);
        List<Chapter> chapters = chapterService.list(new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getNovelId, job.getNovelId())
                .orderByAsc(Chapter::getSequence));
        for (Chapter chapter : chapters) {
            List<NovelPassage> passages = novelPassageService.splitPassage(job.getId(), chapter.getId());
            passageCharacterService.recognizeAndSave(passages);
        }
        log.info("小说Passage构建完成，jobId: {}, novelId: {}, chapterCount: {}",
                job.getId(), job.getNovelId(), chapters.size());
    }

    private Job requireJob(Long jobId) {
        Job job = jobService.getById(jobId);
        if (job == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND, "任务不存在: " + jobId);
        }
        return job;
    }
}
