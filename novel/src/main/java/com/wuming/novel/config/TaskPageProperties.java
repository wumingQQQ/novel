package com.wuming.novel.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 任务中心分页的默认值与服务端上限。 */
@Data
@ConfigurationProperties(prefix = "novel.task")
public class TaskPageProperties {
    private int defaultPageSize = 12;
    private int maxPageSize = 48;
}
