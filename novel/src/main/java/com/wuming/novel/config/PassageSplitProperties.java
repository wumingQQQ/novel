package com.wuming.novel.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Passage 切分配置。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "novel.passage")
public class PassageSplitProperties {

    /**
     * 滑动窗口包含的段落数。
     */
    private int windowSize = 15;

    /**
     * 相邻窗口重叠的段落数。
     */
    private int overlapSize = 3;
}
