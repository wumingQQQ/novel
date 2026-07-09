package com.wuming.novel.integration.storage;

import com.wuming.novel.config.FileUploadProperties;
import com.wuming.novel.exception.FileNotSupportException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 基于本地磁盘的小说文件存储实现。
 * <p>
 * 作为默认实现常驻，用于本地开发、测试以及读取历史LOCAL存储记录。
 */
@Component
@RequiredArgsConstructor
public class LocalNovelFileStorage implements NovelFileStorage {
    public static final String STORAGE_TYPE = "LOCAL";

    private final FileUploadProperties fileUploadProperties;

    @Override
    public String storageType() {
        return STORAGE_TYPE;
    }

    @Override
    public StoredNovelFile store(byte[] bytes, Long userId) throws IOException {
        Path uploadDir = Paths.get(fileUploadProperties.getSavePath())
                .toAbsolutePath()
                .normalize();
        Path filePath = uploadDir.resolve(UUID.randomUUID() + ".txt").normalize();
        if (!filePath.startsWith(uploadDir)) {
            throw new FileNotSupportException("文件名非法");
        }

        Files.createDirectories(uploadDir);
        Files.write(filePath, bytes);
        return new StoredNovelFile(storageType(), filePath.toString(), bytes.length);
    }

    @Override
    public byte[] read(String storagePath) throws IOException {
        return Files.readAllBytes(Paths.get(storagePath));
    }
}
