package com.wuming.novel.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "novel.storage.type", havingValue = "cos")
public class TencentCosConfig {

    @Bean(destroyMethod = "shutdown")
    public COSClient cosClient(TencentCosProperties properties) {
        COSCredentials credentials = new BasicCOSCredentials(
                requireText(properties.getSecretId(), "腾讯云COS SecretId不能为空"),
                requireText(properties.getSecretKey(), "腾讯云COS SecretKey不能为空")
        );
        ClientConfig clientConfig = new ClientConfig(new Region(requireText(properties.getRegion(), "腾讯云COS region不能为空")));
        clientConfig.setHttpProtocol(HttpProtocol.https);
        clientConfig.setConnectionTimeout(properties.getConnectionTimeoutMs());
        clientConfig.setSocketTimeout(properties.getSocketTimeoutMs());
        return new COSClient(credentials, clientConfig);
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}

