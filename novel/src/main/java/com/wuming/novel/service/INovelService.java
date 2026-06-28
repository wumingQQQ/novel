package com.wuming.novel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wuming.novel.domain.entity.Novel;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface INovelService extends IService<Novel> {
    Long saveNovel(MultipartFile file, Long userId) throws IOException;
}
