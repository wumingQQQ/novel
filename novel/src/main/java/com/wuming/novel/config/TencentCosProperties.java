package com.wuming.novel.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "novel.storage.cos")
public class TencentCosProperties {
    private String secretId;
    private String secretKey;
    private String region;
    private String bucketName;
    private String keyPrefix = "novels";
    private int connectionTimeoutMs = 30_000;
    private int socketTimeoutMs = 30_000;
}

