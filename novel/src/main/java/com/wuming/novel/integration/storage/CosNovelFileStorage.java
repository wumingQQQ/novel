package com.wuming.novel.integration.storage;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.wuming.novel.config.TencentCosProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * 基于腾讯云COS的小说文件存储实现。
 * <p>
 * 文件对象key只表达存储位置，不绑定用户目录；上传用户只作为对象元信息写入COS。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "novel.storage.type", havingValue = "cos")
public class CosNovelFileStorage implements NovelFileStorage {
    public static final String STORAGE_TYPE = "COS";

    private final COSClient cosClient;
    private final TencentCosProperties properties;

    @Override
    public String storageType() {
        return STORAGE_TYPE;
    }

    @Override
    public StoredNovelFile store(byte[] bytes, Long userId) throws IOException {
        String objectKey = objectKey();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        metadata.setContentType("text/plain; charset=UTF-8");
        if (userId != null) {
            metadata.addUserMetadata("uploader-id", String.valueOf(userId));
        }
        try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
            PutObjectRequest request = new PutObjectRequest(
                    requireBucketName(),
                    objectKey,
                    inputStream,
                    metadata
            );
            cosClient.putObject(request);
        }
        return new StoredNovelFile(storageType(), objectKey, bytes.length);
    }

    @Override
    public byte[] read(String storagePath) throws IOException {
        try (COSObject cosObject = cosClient.getObject(requireBucketName(), storagePath);
             InputStream inputStream = cosObject.getObjectContent()) {
            return inputStream.readAllBytes();
        }
    }

    private String objectKey() {
        String prefix = normalizePrefix(properties.getKeyPrefix());
        return prefix + "/" + UUID.randomUUID() + ".txt";
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "novels";
        }
        return prefix.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private String requireBucketName() {
        String bucketName = properties.getBucketName();
        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalArgumentException("腾讯云COS bucketName不能为空");
        }
        return bucketName.trim();
    }
}
