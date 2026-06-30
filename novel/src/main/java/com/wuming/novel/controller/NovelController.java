package com.wuming.novel.controller;

import com.wuming.novel.domain.dto.ApiResonse;
import com.wuming.novel.domain.dto.CreateJobRequest;
import com.wuming.novel.observability.TraceContext;
import com.wuming.novel.pipeline.run.JobSubmitStatus;
import com.wuming.novel.pipeline.run.PipelineJobRunner;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.INovelService;
import com.wuming.novel.sse.JobProgress;
import com.wuming.novel.sse.JobProgressService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.validation.constraints.NotNull;
import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/novel")
public class NovelController {

    private final INovelService novelService;
    private final IJobService jobService;
    private final PipelineJobRunner pipelineJobRunner;
    private final JobProgressService jobProgressService;

    public NovelController(
            INovelService novelService,
            IJobService jobService,
            PipelineJobRunner pipelineJobRunner,
            JobProgressService jobProgressService
    ) {
        this.novelService = novelService;
        this.jobService = jobService;
        this.pipelineJobRunner = pipelineJobRunner;
        this.jobProgressService = jobProgressService;
    }

    /**
     * 上传小说文件并保存小说元信息。
     */
    @PostMapping
    public ApiResonse<Long> uploadNovel(@NotNull MultipartFile file, Long userId) throws IOException {
        try (TraceContext.MdcScope ignoredUser = TraceContext.putUserId(userId)) {
            long start = System.currentTimeMillis();
            Long novelId = novelService.saveNovel(file, userId);
            try (TraceContext.MdcScope ignoredNovel = TraceContext.putNovelId(novelId)) {
                log.info("小说上传完成，fileName: {}, fileSize: {}, costMs: {}",
                        file.getOriginalFilename(), file.getSize(),
                        System.currentTimeMillis() - start);
            }
            return ApiResonse.success(novelId);
        }
    }

    /**
     * 基于小说和用户创建画像构建任务。
     */
    @PostMapping("/createJob")
    public ApiResonse<Long> createJob(@RequestBody CreateJobRequest request) {
        try (TraceContext.MdcScope ignoredUser = TraceContext.putUserId(request.getUserId());
             TraceContext.MdcScope ignoredNovel = TraceContext.putNovelId(request.getNovelId())) {
            long start = System.currentTimeMillis();
            Long jobId = jobService.createJob(request);
            try (TraceContext.MdcScope ignoredJob = TraceContext.putJobId(jobId)) {
                log.info("画像构建任务创建完成，costMs: {}", System.currentTimeMillis() - start);
            }
            return ApiResonse.success(jobId);
        }
    }

    /**
     * 异步提交小说处理流程；如果同一 job 正在运行，则直接返回 running。
     */
    @PostMapping("/process/{jobId}")
    public ApiResonse<String> processJob(@PathVariable("jobId") Long jobId) {
        try (TraceContext.MdcScope ignoredJob = TraceContext.putJobId(jobId)) {
            JobSubmitStatus status = pipelineJobRunner.submit(jobId);
            log.info("任务处理提交完成，submitStatus: {}", status);
            return ApiResonse.success(status.responseValue());
        }
    }

    /**
     * 仅在任务失败时异步重启流程；任务运行中则直接返回 running。
     */
    @PostMapping("/redo/{jobId}")
    public ApiResonse<String> redoJob(@PathVariable("jobId") Long jobId) {
        try (TraceContext.MdcScope ignoredJob = TraceContext.putJobId(jobId)) {
            JobSubmitStatus status = pipelineJobRunner.redo(jobId);
            log.info("任务重跑提交完成，submitStatus: {}", status);
            return ApiResonse.success(status.responseValue());
        }
    }

    /**
     * 查询当前任务进度；本地没有进度时会尝试从 Redis 恢复。
     */
    @GetMapping("/progress/{jobId}")
    public ApiResonse<JobProgress> getProgress(@PathVariable("jobId") Long jobId) {
        try (TraceContext.MdcScope ignoredJob = TraceContext.putJobId(jobId)) {
            JobProgress progress = jobProgressService.getOrInitProgress(jobId);
            log.debug("任务进度查询完成，state: {}", progress.getState());
            return ApiResonse.success(progress);
        }
    }

    /**
     * 建立 SSE 订阅，并立即推送一次当前任务进度。
     */
    @GetMapping(value = "/progress/{jobId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeProgress(@PathVariable("jobId") Long jobId) {
        try (TraceContext.MdcScope ignoredJob = TraceContext.putJobId(jobId)) {
            log.info("建立任务进度SSE订阅");
            return jobProgressService.subscribe(jobId);
        }
    }
}
