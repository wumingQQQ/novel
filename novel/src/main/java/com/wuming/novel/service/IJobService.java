package com.wuming.novel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wuming.novel.domain.dto.CreateJobRequest;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.enums.JobStage;

public interface IJobService extends IService<Job> {
    int createJob(CreateJobRequest request);
    void advanceStage(int jobId, JobStage stage);
}
