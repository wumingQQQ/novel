package com.wuming.novel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import com.wuming.novel.domain.dto.CreateJobRequest;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.Novel;
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
     * 为当前登录用户创建画像构建任务。
     *
     * @param request 包含小说id和角色名
     * @param userId 当前登录用户id
     * @return jobId
     */
    @Override
    public Long createJob(CreateJobRequest request, Long userId) {
        if (request == null) {
            throw new IllegalArgumentException("创建任务请求不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        Long novelId = request.getNovelId();
        if (novelId == null) {
            throw new IllegalArgumentException("novelId不能为空");
        }
        Novel novel = novelService.getById(novelId);
        if (novel == null) {
            throw new BusinessException(ErrorCode.NOVEL_NOT_FOUND, "您指定的小说不存在");
        }
        if (!userId.equals(novel.getUploaderId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能基于其他用户的小说创建任务");
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

    /**
     * 查询并校验任务属于当前登录用户。
     */
    @Override
    public Job requireOwnedJob(Long jobId, Long userId) {
        if (jobId == null) {
            throw new IllegalArgumentException("jobId不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        Job job = getById(jobId);
        if (job == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND, "任务不存在: " + jobId);
        }
        if (!userId.equals(job.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能访问其他用户的任务");
        }
        return job;
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

