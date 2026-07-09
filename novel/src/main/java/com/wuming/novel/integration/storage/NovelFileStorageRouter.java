package com.wuming.novel.integration.storage;

import com.wuming.novel.domain.entity.Novel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 小说文件存储路由。
 * <p>
 * 上传时使用当前默认存储类型；读取时优先使用小说记录中的存储类型，保证历史文件可继续读取。
 */
@Component
public class NovelFileStorageRouter {
    private final Map<String, NovelFileStorage> storageMap;
    private final String defaultStorageType;

    public NovelFileStorageRouter(List<NovelFileStorage> storages,
                                  @Value("${novel.storage.type:local}") String defaultStorageType) {
        this.storageMap = storages.stream()
                .collect(Collectors.toUnmodifiableMap(NovelFileStorage::storageType, Function.identity()));
        this.defaultStorageType = normalize(defaultStorageType);
    }

    public StoredNovelFile store(byte[] bytes, Long userId) throws IOException {
        return requireStorage(defaultStorageType).store(bytes, userId);
    }

    public byte[] read(Novel novel) throws IOException {
        String storageType = normalize(novel.getStorageType());
        if (storageType == null) {
            storageType = defaultStorageType;
        }
        String storagePath = novel.getObjectKey() == null || novel.getObjectKey().isBlank()
                ? novel.getFilePath()
                : novel.getObjectKey();
        return requireStorage(storageType).read(storagePath);
    }

    private NovelFileStorage requireStorage(String storageType) {
        NovelFileStorage storage = storageMap.get(storageType);
        if (storage == null) {
            throw new IllegalStateException("未配置小说文件存储实现: " + storageType);
        }
        return storage;
    }

    private String normalize(String storageType) {
        if (storageType == null || storageType.isBlank()) {
            return null;
        }
        return storageType.trim().toUpperCase(Locale.ROOT);
    }
}
