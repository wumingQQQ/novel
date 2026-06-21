package com.wuming.novel.controller;

import com.wuming.novel.domain.dto.ApiResonse;
import com.wuming.novel.domain.dto.CreateJobRequest;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.INovelService;
import com.wuming.novel.serviceImpl.PipelineService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotNull;
import java.io.IOException;

@RestController
@RequestMapping("/novel")
public class NovelController {

    private final INovelService novelService;
    private final IJobService jobService;
    private final PipelineService pipelineService;

    public NovelController(INovelService novelService, IJobService jobService, PipelineService pipelineService) {
        this.novelService = novelService;
        this.jobService = jobService;
        this.pipelineService = pipelineService;
    }

    @PostMapping
    public ApiResonse<Long> uploadNovel(@NotNull MultipartFile file) throws IOException {
        Long novelId = novelService.saveNovel(file);
        return ApiResonse.success(novelId);
    }

    @RequestMapping("/createJob")
    public ApiResonse<Long> createJob(@RequestBody CreateJobRequest request) {
        Long jobId = jobService.createJob(request);
        return ApiResonse.success(jobId);
    }

    @RequestMapping("/process/{jobId}")
    public ApiResonse<String> processJob(@PathVariable("jobId") Long jobId) {
        boolean success = pipelineService.handleNovel(jobId);
        String message = success ? "success" : "fail";
        return ApiResonse.success(message);
    }

    @RequestMapping("/redo/{jobId}")
    public ApiResonse<String> redoJob(@PathVariable("jobId") Long jobId) {
        boolean success = pipelineService.handleNovel(jobId);
        String message = success ? "success" : "fail";
        return ApiResonse.success(message);
    }

}
