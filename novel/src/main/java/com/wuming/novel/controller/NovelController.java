package com.wuming.novel.controller;

import com.wuming.novel.domain.dto.ApiResonse;
import com.wuming.novel.domain.dto.CreateJobRequest;
import com.wuming.novel.pipeline.run.JobSubmitStatus;
import com.wuming.novel.pipeline.run.PipelineJobRunner;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.INovelService;
import com.wuming.novel.sse.JobProgress;
import com.wuming.novel.sse.JobProgressService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotNull;
import java.io.IOException;

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

    @PostMapping
    public ApiResonse<Long> uploadNovel(@NotNull MultipartFile file) throws IOException {
        Long novelId = novelService.saveNovel(file);
        return ApiResonse.success(novelId);
    }

    @PostMapping("/createJob")
    public ApiResonse<Long> createJob(@RequestBody CreateJobRequest request) {
        Long jobId = jobService.createJob(request);
        return ApiResonse.success(jobId);
    }

    /**
     * 异步提交小说处理流程；如果同一 job 正在运行，则直接返回 running。
     */
    @PostMapping("/process/{jobId}")
    public ApiResonse<String> processJob(@PathVariable("jobId") Long jobId) {
        JobSubmitStatus status = pipelineJobRunner.submit(jobId);
        return ApiResonse.success(status.responseValue());
    }

    /**
     * 仅在任务失败时异步重启流程；任务运行中则直接返回 running。
     */
    @PostMapping("/redo/{jobId}")
    public ApiResonse<String> redoJob(@PathVariable("jobId") Long jobId) {
        JobSubmitStatus status = pipelineJobRunner.redo(jobId);
        return ApiResonse.success(status.responseValue());
    }

    /**
     * 查询当前任务进度；本地没有进度时会尝试从 Redis 恢复。
     */
    @GetMapping("/progress/{jobId}")
    public ApiResonse<JobProgress> getProgress(@PathVariable("jobId") Long jobId) {
        return ApiResonse.success(jobProgressService.getOrInitProgress(jobId));
    }

    /**
     * 建立 SSE 订阅，并立即推送一次当前任务进度。
     */
    @GetMapping(value = "/progress/{jobId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeProgress(@PathVariable("jobId") Long jobId) {
        return jobProgressService.subscribe(jobId);
    }

}
