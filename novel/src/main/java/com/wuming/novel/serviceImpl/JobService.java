package com.wuming.novel.serviceImpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.domain.dto.CreateJobRequest;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.infrastructure.mapper.JobMapper;
import com.wuming.novel.integration.rpc.user.UserContextService;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.INovelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService extends ServiceImpl<JobMapper, Job> implements IJobService {
    private final INovelService novelService;
    private final UserContextService userContextService;

    /**
     * 创建job
     * @param request 包含小说id，用户id，角色名
     * @return jobId
     */
    @Override
    public Long createJob(CreateJobRequest request) {
        Long novelId = request.getNovelId();
        Long userId = request.getUserId();
        if(novelService.getById(novelId) == null){
            throw new IllegalArgumentException("您指定的小说不存在");
        }
        userContextService.requireUser(userId);
        Job job = new Job();
        job.setNovelId(novelId);
        job.setUserId(userId);
        job.setTargetName(request.getTargetName());
        job.setProtagonistName(request.getProtagonistName());
        job.setStage(JobStage.PENDING);
        save(job);
        return job.getId();
    }

    public void advanceStage(Long jobId, JobStage stage){
        Job job = getById(jobId);
        job.setStage(stage);
        updateById(job);
        if(stage == JobStage.COMPLETE){
            log.info("job: {}完成", jobId);
            return;
        }
        log.info("job: {}完成{}阶段", jobId, stage.name());
    }
}
