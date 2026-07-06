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
     * 默认使用滑动窗口切分，避免依赖 LLM 判断场景边界。
     */
    private SplitStrategy splitStrategy = SplitStrategy.SLIDING_WINDOW;

    /**
     * 滑动窗口包含的段落数。
     */
    private int windowSize = 15;

    /**
     * 相邻窗口重叠的段落数。
     */
    private int overlapSize = 3;

    public enum SplitStrategy {
        /**
         * 固定窗口切分。
         */
        SLIDING_WINDOW,

        /**
         * 使用章节分析阶段产出的 LLM 场景边界切分。
         */
        LLM_SCENE
    }
}
