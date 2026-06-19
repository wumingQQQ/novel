package com.wuming.novel.controller;

import com.wuming.novel.domain.dto.ApiResonse;
import com.wuming.novel.domain.dto.CreateJobRequest;
import com.wuming.novel.service.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotNull;
import java.io.IOException;

@RestController
@RequestMapping("/novel")
public class NovelController {

    private final INovelService novelService;
    private final IJobService jobService;

    public NovelController(INovelService novelService, IJobService jobService) {
        this.novelService = novelService;
        this.jobService = jobService;
    }

    @PostMapping
    public ApiResonse<Integer> uploadNovel(@NotNull MultipartFile file) throws IOException {
        int novelId = novelService.saveNovel(file);
        return ApiResonse.success(novelId);
    }

    @RequestMapping("/createJob")
    public ApiResonse<Integer> createJob(@RequestBody CreateJobRequest request) throws IOException {
        int jobId = jobService.createJob(request);
        return ApiResonse.success(jobId);
    }





}
