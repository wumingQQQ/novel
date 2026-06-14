package com.wuming.novel.controller;

import com.wuming.novel.domain.dto.ApiResonse;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.INovelService;
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

    public NovelController(INovelService novelService, IChapterService chapterService) {
        this.novelService = novelService;
        this.chapterService = chapterService;
    }

    @PostMapping
    public ApiResonse<Integer> uploadNovel(@NotNull MultipartFile file) throws IOException {
        int novelId = novelService.saveNovel(file);
        return ApiResonse.success(novelId);
    }

    @RequestMapping("/{id}")
    public ApiResonse<String> splitChapters(@PathVariable int id) {

    }


}
