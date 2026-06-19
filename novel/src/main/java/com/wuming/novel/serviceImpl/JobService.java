package com.wuming.novel.serviceImpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.domain.dto.CreateJobRequest;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.mapper.JobMapper;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.INovelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JobService extends ServiceImpl<JobMapper, Job> implements IJobService {
    private final INovelService novelService;

    public JobService(INovelService novelService) {
        this.novelService = novelService;
    }

    @Override
    public int createJob(CreateJobRequest request) {
        int novelId = request.getNovelId();
        if(novelService.getById(novelId) == null){
            throw new IllegalArgumentException("您指定的小说不存在");
        }
        Job job = new Job();
        job.setNovelId(novelId);
        job.setTargetName(request.getTargetName());
        job.setProtagonistName(request.getProtagonistName());
        job.setStage(JobStage.PENDING);
        save(job);
        return job.getId();
    }

    public void advanceStage(int jobId, JobStage stage){
        Job job = getById(jobId);
        job.setStage(stage);
        updateById(job);
        if(stage == JobStage.COMPLETE){
            log.info("job: {}完成", jobId);
            return;
        }
        log.info("job: {}进入{}阶段", jobId, stage.name());
    }
}
