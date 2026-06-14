package com.wuming.novel.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Data
@Component
@ConfigurationProperties(prefix = "novel.upload")
public class FileUploadProperties {
    private DataSize maxFileSize;
    private String savePath;
}
