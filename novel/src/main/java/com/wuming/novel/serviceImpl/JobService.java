package com.wuming.novel.serviceImpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.domain.dto.CreateJobRequest;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.mapper.JobMapper;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.INovelService;
import org.springframework.stereotype.Service;

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
}
