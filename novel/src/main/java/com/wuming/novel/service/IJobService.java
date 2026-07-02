package com.wuming.novel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wuming.novel.domain.dto.CreateJobRequest;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.enums.JobStage;

public interface IJobService extends IService<Job> {
    Long createJob(CreateJobRequest request, Long userId);

    Job requireOwnedJob(Long jobId, Long userId);

    void advanceStage(Long jobId, JobStage stage);
}
