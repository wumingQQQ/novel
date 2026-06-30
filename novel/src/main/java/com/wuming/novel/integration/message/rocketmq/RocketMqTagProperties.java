package com.wuming.novel.integration.message.rocketmq;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "novel.mq.tags")
public class RocketMqTagProperties {
    private String singleChapterSplitCompleted;
    private String jobFinished;
}
