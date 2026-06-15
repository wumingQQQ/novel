package com.wuming.novel.serviceImpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.config.FileUploadProperties;
import com.wuming.novel.domain.entity.Novel;
import com.wuming.novel.exception.FileNotSupportException;
import com.wuming.novel.exception.FileTooLargeException;
import com.wuming.novel.mapper.NovelMapper;
import com.wuming.novel.service.INovelService;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class NovelService extends ServiceImpl<NovelMapper, Novel> implements INovelService {

    private final FileUploadProperties fileUploadProperties;

    public NovelService(FileUploadProperties fileUploadProperties) {
        this.fileUploadProperties = fileUploadProperties;
    }

    @Override
    public int saveNovel(MultipartFile file) throws IOException {
        if(file.getSize() > fileUploadProperties.getMaxFileSize().toBytes()){
            throw new FileTooLargeException("文件过大，请确保小于10MB");
        }
        String fileName = file.getOriginalFilename();
        String ext = FilenameUtils.getExtension(fileName);
        if(!"txt".equals(ext)){
            throw new FileNotSupportException("文件格式不支持，请上传txt格式的文件");
        }

        String savePath = fileUploadProperties.getSavePath();
        Path  path = Paths.get(savePath);
        Path filePath = path.resolve(fileName);

        // 保存文件
        Files.createDirectories(filePath);
        file.transferTo(filePath);

        Novel novel = new Novel();
        novel.setName(FilenameUtils.getBaseName(fileName));
        novel.setFilePath(filePath.toString());

        save(novel);
        return novel.getId();
    }
}
