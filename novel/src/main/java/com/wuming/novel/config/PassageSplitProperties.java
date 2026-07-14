package com.wuming.novel.config;

import com.wuming.novel.passage.split.PassageSplitStrategyType;
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
     * 当前启用的Passage切分策略。
     */
    private PassageSplitStrategyType splitStrategy = PassageSplitStrategyType.AUTO;

    /**
     * 自动路由切分策略的配置。
     */
    private Auto auto = new Auto();

    /**
     * 按段落滑动窗口切分的配置。
     */
    private Paragraph paragraph = new Paragraph();

    /**
     * 按字符长度重叠切分的配置。
     */
    private CharacterWindow character = new CharacterWindow();

    @Getter
    @Setter
    public static class Auto {
        /**
         * 用于判断整本小说切分策略的随机参考章节数。
         */
        private int sampleChapterCount = 20;

        /**
         * 单章非空段落数超过该值时，该章节倾向段落窗口切分。
         */
        private int paragraphCountThreshold = 80;

        /**
         * 单章平均非空行字数低于该值时，该章节倾向段落窗口切分。
         */
        private int averageLineCharsThreshold = 50;
    }

    @Getter
    @Setter
    public static class Paragraph {
        /**
         * 滑动窗口包含的段落数。
         */
        private int windowSize = 15;

        /**
         * 相邻窗口重叠的段落数。
         */
        private int overlapSize = 3;
    }

    @Getter
    @Setter
    public static class CharacterWindow {
        /**
         * 单个Passage建议最大字符数。
         */
        private int maxChars = 450;

        /**
         * 相邻Passage之间尽量保留的重叠字符数。
         */
        private int overlapChars = 90;
    }
}
