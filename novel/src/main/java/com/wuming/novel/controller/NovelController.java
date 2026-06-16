package com.wuming.novel.controller;

import com.wuming.novel.domain.dto.ApiResonse;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.ILayerService;
import com.wuming.novel.service.INovelService;
import com.wuming.novel.service.ISceneService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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

    public NovelController(INovelService novelService, IChapterService chapterService, ISceneService sceneService, ILayerService layerService) {
        this.novelService = novelService;
        this.chapterService = chapterService;
        this.sceneService = sceneService;
        this.layerService = layerService;
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

    @RequestMapping("/scene/{id}")
    public ApiResonse<String> splitScene(@PathVariable int id) throws IOException {
        sceneService.splitScene(id);
        return ApiResonse.success("");
    }

    @RequestMapping("/layer/{id}")
    public ApiResonse<String> splitLayer(@PathVariable int id) throws IOException {
        layerService.splitLayer(id);
        return ApiResonse.success("");
    }




}
