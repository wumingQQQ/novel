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
import java.util.UUID;

@Service
public class NovelService extends ServiceImpl<NovelMapper, Novel> implements INovelService {

    private final FileUploadProperties fileUploadProperties;

    public NovelService(FileUploadProperties fileUploadProperties) {
        this.fileUploadProperties = fileUploadProperties;
    }

    /**
     * 上传小说
     * @return 小说id
     */
    @Override
    public Long saveNovel(MultipartFile file, Long userId) throws IOException {
        if(file.isEmpty()) {
            throw new FileNotSupportException("文件不能为空");
        }
        // TODO 查询用户是否存在
        if(userId == null) {
            throw new IllegalArgumentException("用户不能为空");
        }
        if(file.getSize() > fileUploadProperties.getMaxFileSize().toBytes()){
            throw new FileTooLargeException("文件过大，请确保小于10MB");
        }
        String originalFilename = file.getOriginalFilename();
        String safeOriginalName = FilenameUtils.getName(originalFilename);
        String ext = FilenameUtils.getExtension(safeOriginalName);
        if(!"txt".equals(ext)){
            throw new FileNotSupportException("文件格式不支持，请上传txt格式的文件");
        }

        String baseName = FilenameUtils.getBaseName(safeOriginalName);
        String storedFileName = UUID.randomUUID() + ".txt";

        Path uploadDir = Paths.get(fileUploadProperties.getSavePath())
                .toAbsolutePath()
                .normalize();
        Path filePath = uploadDir.resolve(storedFileName).normalize();

        if(!filePath.startsWith(uploadDir)){
            throw new FileNotSupportException("文件名非法");
        }

        // 保存文件
        Files.createDirectories(uploadDir);
        file.transferTo(filePath);

        Novel novel = new Novel();
        novel.setName(baseName);
        novel.setUploaderId(userId);
        novel.setFilePath(filePath.toString());

        save(novel);
        return novel.getId();
    }
}
