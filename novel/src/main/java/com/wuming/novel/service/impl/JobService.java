package com.wuming.novel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import com.wuming.common.web.PageResponse;
import com.wuming.novel.config.TaskPageProperties;
import com.wuming.novel.domain.dto.CreateJobRequest;
import com.wuming.novel.domain.dto.MyJobResponse;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.Novel;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.domain.enums.JobStatus;
import com.wuming.novel.infrastructure.mapper.JobMapper;
import com.wuming.novel.integration.rpc.user.UserContextService;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.INovelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService extends ServiceImpl<JobMapper, Job> implements IJobService {
    private final INovelService novelService;
    private final UserContextService userContextService;
    private final TaskPageProperties taskPageProperties;

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
        // 小说是公共构建素材；任务归属创建它的用户，而不是小说上传者。
        userContextService.requireUser(userId);

        Job job = new Job();
        job.setNovelId(novelId);
        job.setUserId(userId);
        job.setTargetName(request.getTargetName());
        job.setProtagonistName(request.getProtagonistName());
        job.setStage(JobStage.PENDING);
        job.setStatus(JobStatus.PENDING);
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
        if (job == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND, "任务不存在: " + jobId);
        }
        job.setStage(stage);
        updateById(job);
        if(stage == JobStage.COMPLETE){
            log.debug("任务阶段已推进，jobId: {}, stage: {}", jobId, stage);
            return;
        }
        log.debug("任务阶段已推进，jobId: {}, stage: {}", jobId, stage);
    }

    /** 标记任务开始新一轮执行，并清除上一轮失败信息。 */
    @Override
    public void markRunning(Long jobId) {
        updateStatus(jobId, JobStatus.RUNNING, null, LocalDateTime.now(), null);
    }

    /** 标记任务已完成。 */
    @Override
    public void markDone(Long jobId) {
        updateStatus(jobId, JobStatus.DONE, null, null, LocalDateTime.now());
    }

    /** 标记任务失败并保存可展示的失败原因。 */
    @Override
    public void markFailed(Long jobId, String failureReason) {
        updateStatus(jobId, JobStatus.FAILED, failureReason, null, LocalDateTime.now());
    }

    /** 分页查询当前用户创建的任务，状态筛选为空时返回全部任务。 */
    @Override
    public PageResponse<MyJobResponse> listMyJobs(Long userId, int page, Integer size, String status) {
        int normalizedPage = Math.max(1, page);
        int requestedSize = size == null ? taskPageProperties.getDefaultPageSize() : size;
        int normalizedSize = Math.min(taskPageProperties.getMaxPageSize(), Math.max(1, requestedSize));
        JobStatus jobStatus = parseStatus(status);
        Page<Job> result = page(Page.of(normalizedPage, normalizedSize), new LambdaQueryWrapper<Job>()
                .eq(Job::getUserId, userId).eq(jobStatus != null, Job::getStatus, jobStatus)
                .orderByDesc(Job::getCreateTime).orderByDesc(Job::getId));
        Set<Long> novelIds = result.getRecords().stream().map(Job::getNovelId).collect(Collectors.toSet());
        Map<Long, String> novelNames = novelIds.isEmpty() ? Map.of() : novelService.listByIds(novelIds).stream()
                .collect(Collectors.toMap(Novel::getId, Novel::getName, (left, right) -> left));
        var items = result.getRecords().stream().map(job -> new MyJobResponse(job.getId(), job.getNovelId(),
                novelNames.getOrDefault(job.getNovelId(), "已删除小说"), job.getProtagonistName(), job.getTargetName(),
                job.getStage(), job.getStatus(), job.getFailureReason(), job.getCreateTime(), job.getStartedTime(),
                job.getFinishedTime())).toList();
        return new PageResponse<>(items, result.getTotal(), normalizedPage, normalizedSize);
    }

    private void updateStatus(Long jobId, JobStatus status, String failureReason,
                              LocalDateTime startedTime, LocalDateTime finishedTime) {
        Job job = getById(jobId);
        if (job == null) throw new BusinessException(ErrorCode.JOB_NOT_FOUND, "任务不存在: " + jobId);
        job.setStatus(status);
        job.setFailureReason(failureReason);
        if (startedTime != null) job.setStartedTime(startedTime);
        if (finishedTime != null) job.setFinishedTime(finishedTime);
        updateById(job);
    }

    private JobStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try { return JobStatus.valueOf(status.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("不支持的任务状态: " + status); }
    }
}

