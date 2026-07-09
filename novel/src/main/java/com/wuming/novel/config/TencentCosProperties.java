package com.wuming.novel.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "novel.storage.cos")
public class TencentCosProperties {
    /**
     * 腾讯云访问密钥ID。
     */
    private String secretId;
    /**
     * 腾讯云访问密钥Key。
     */
    private String secretKey;
    /**
     * COS地域，例如ap-guangzhou。
     */
    private String region;
    /**
     * 存储桶名称，格式通常为bucket-appid。
     */
    private String bucketName;
    /**
     * 小说文件对象key前缀，不包含用户维度。
     */
    private String keyPrefix = "novels";
    /**
     * COS客户端连接超时时间。
     */
    private int connectionTimeoutMs = 30_000;
    /**
     * COS客户端读写超时时间。
     */
    private int socketTimeoutMs = 30_000;
}
