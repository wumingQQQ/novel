package com.wuming.novel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wuming.common.web.PageResponse;
import com.wuming.novel.domain.dto.CreateJobRequest;
import com.wuming.novel.domain.dto.MyJobResponse;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.enums.JobStage;

public interface IJobService extends IService<Job> {
    Long createJob(CreateJobRequest request, Long userId);

    Job requireOwnedJob(Long jobId, Long userId);

    void advanceStage(Long jobId, JobStage stage);

    void markRunning(Long jobId);

    void markDone(Long jobId);

    void markFailed(Long jobId, String failureReason);

    PageResponse<MyJobResponse> listMyJobs(Long userId, int page, Integer size, String status);
}
