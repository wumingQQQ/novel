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
    private final IChapterService chapterService;
    private final ISceneService sceneService;
    private final ILayerService layerService;
    private final IScenePoolService scenePoolService;
    private final IJobService jobService;

    public NovelController(INovelService novelService, IChapterService chapterService, ISceneService sceneService, ILayerService layerService, IScenePoolService scenePoolService, IJobService jobService) {
        this.novelService = novelService;
        this.chapterService = chapterService;
        this.sceneService = sceneService;
        this.layerService = layerService;
        this.scenePoolService = scenePoolService;
        this.jobService = jobService;
    }

    @PostMapping
    public ApiResonse<Integer> uploadNovel(@NotNull MultipartFile file) throws IOException {
        int novelId = novelService.saveNovel(file);
        return ApiResonse.success(novelId);
    }

    @RequestMapping("/{id}")
    public ApiResonse<String> splitChapter(@PathVariable int id) throws IOException {
        chapterService.splitChapter(id);
        return ApiResonse.success("");
    }

    @RequestMapping("/createJob")
    public ApiResonse<Integer> createJob(@RequestBody CreateJobRequest request) throws IOException {
        int jobId = jobService.createJob(request);
        return ApiResonse.success(jobId);
    }

    @RequestMapping("/scene/{id}")
    public ApiResonse<String> splitScene(@PathVariable int id){
        sceneService.splitScene(id);
        return ApiResonse.success("");
    }

    @RequestMapping("/layer/{id}")
    public ApiResonse<String> splitLayer(@PathVariable int id){
        layerService.splitLayer(id);
        return ApiResonse.success("");
    }

    @RequestMapping("/pool/{id}")
    public ApiResonse<String> scenePool(@PathVariable int id){
        scenePoolService.divideSceneIntoPool(id);
        return ApiResonse.success("");
    }




}
